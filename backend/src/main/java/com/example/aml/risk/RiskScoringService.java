package com.example.aml.risk;

import com.example.aml.audit.AuditService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;

@Service
public class RiskScoringService {
    static final String CUSTOMER_MODEL = "CUSTOMER_RULE_V1";
    static final String TRANSACTION_MODEL = "TRANSACTION_RULE_V1";

    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final AuditService audit;

    public RiskScoringService(JdbcTemplate jdbc, ObjectMapper json, AuditService audit) {
        this.jdbc = jdbc;
        this.json = json;
        this.audit = audit;
    }

    @Transactional
    public ScoreResult calculateCustomer(UUID customerId, UUID operatorId) {
        List<Map<String, Object>> customers = jdbc.queryForList(
                "select id,risk_level,country_code from aml_customer where id=? for update", customerId);
        if (customers.isEmpty()) throw new NoSuchElementException("客户不存在");
        Map<String, Object> customer = customers.getFirst();
        List<Factor> factors = new ArrayList<>();
        String profileRisk = String.valueOf(customer.get("risk_level"));
        add(factors, "PROFILE_RISK", "HIGH".equals(profileRisk) ? 20 : "MEDIUM".equals(profileRisk) ? 10 : 0,
                "客户资料风险等级=" + profileRisk);
        if (customer.get("country_code") == null) add(factors, "COUNTRY_MISSING", 5, "国家/地区信息缺失");

        long alerts = count("select count(*) from aml_alert where customer_id=?", customerId);
        add(factors, "ALERT_COUNT", Math.min(24, alerts * 8), "历史预警数量=" + alerts);

        Map<String, Long> screening = screeningCounts(customerId);
        add(factors, "CONFIRMED_REAL_SCREENING", screening.getOrDefault("REAL_CONFIRMED", 0L) > 0 ? 40 : 0,
                "真实名单确认命中=" + screening.getOrDefault("REAL_CONFIRMED", 0L));
        add(factors, "CONFIRMED_SIMULATED_SCREENING", screening.getOrDefault("SIMULATED_CONFIRMED", 0L) > 0 ? 10 : 0,
                "模拟名单确认命中（仅教学因子）=" + screening.getOrDefault("SIMULATED_CONFIRMED", 0L));
        add(factors, "PENDING_REAL_SCREENING", screening.getOrDefault("REAL_PENDING", 0L) > 0 ? 12 : 0,
                "待复核真实候选=" + screening.getOrDefault("REAL_PENDING", 0L));
        add(factors, "PENDING_SIMULATED_SCREENING", screening.getOrDefault("SIMULATED_PENDING", 0L) > 0 ? 3 : 0,
                "待复核模拟候选=" + screening.getOrDefault("SIMULATED_PENDING", 0L));

        BigDecimal totalAmount = jdbc.queryForObject("select coalesce(sum(t.amount),0) from aml_transaction t "
                + "join aml_account_customer ac on ac.account_id=t.account_id where ac.customer_id=?",
                BigDecimal.class, customerId);
        int volumePoints = totalAmount.compareTo(new BigDecimal("1000000")) >= 0 ? 15
                : totalAmount.compareTo(new BigDecimal("100000")) >= 0 ? 5 : 0;
        add(factors, "TRANSACTION_VOLUME", volumePoints, "累计交易金额=" + totalAmount.toPlainString());

        return saveCustomer(customerId, operatorId, factors, dataMode(screening));
    }

    @Transactional
    public ScoreResult calculateTransaction(UUID transactionId, UUID operatorId) {
        List<Map<String, Object>> rows = jdbc.queryForList("select id,amount,counterparty_account from aml_transaction "
                + "where id=? for update", transactionId);
        if (rows.isEmpty()) throw new NoSuchElementException("交易不存在");
        Map<String, Object> transaction = rows.getFirst();
        BigDecimal amount = (BigDecimal) transaction.get("amount");
        List<Factor> factors = new ArrayList<>();
        int amountPoints = amount.compareTo(new BigDecimal("100000")) >= 0 ? 35
                : amount.compareTo(new BigDecimal("50000")) >= 0 ? 20
                : amount.compareTo(new BigDecimal("10000")) >= 0 ? 8 : 0;
        add(factors, "AMOUNT", amountPoints, "交易金额=" + amount.toPlainString());
        if (transaction.get("counterparty_account") == null) add(factors, "COUNTERPARTY_MISSING", 5, "对手方账号缺失");
        long ruleHits = count("select count(*) from aml_rule_hit_transaction where transaction_id=?", transactionId);
        add(factors, "RULE_HITS", Math.min(40, ruleHits * 20), "规则命中数量=" + ruleHits);

        List<Map<String, Object>> customerRisks = jdbc.queryForList("select cr.score,cr.source_data_mode from aml_transaction t "
                + "join aml_account_customer ac on ac.account_id=t.account_id "
                + "join aml_customer_risk cr on cr.customer_id=ac.customer_id where t.id=? order by cr.score desc limit 1",
                transactionId);
        BigDecimal customerScore = customerRisks.isEmpty() ? BigDecimal.ZERO
                : (BigDecimal) customerRisks.getFirst().get("score");
        add(factors, "CUSTOMER_RISK", customerScore.multiply(new BigDecimal("0.30")).intValue(),
                "关联客户当前风险分=" + customerScore.toPlainString());
        String mode = customerRisks.isEmpty() ? "NONE" : String.valueOf(customerRisks.getFirst().get("source_data_mode"));
        return saveTransaction(transactionId, operatorId, factors, mode);
    }

    public ScoreResult currentCustomer(UUID customerId) {
        return one("select customer_id subject_id,score,risk_level,model_version,explanation,source_data_mode,updated_at assessed_at "
                + "from aml_customer_risk where customer_id=?", customerId);
    }
    public List<ScoreResult> customerHistory(UUID customerId) {
        return many("select customer_id subject_id,score,risk_level,model_version,explanation,source_data_mode,assessed_at "
                + "from aml_customer_risk_history where customer_id=? order by assessed_at desc", customerId);
    }
    public ScoreResult currentTransaction(UUID transactionId) {
        return one("select transaction_id subject_id,score,risk_level,model_version,explanation,source_data_mode,updated_at assessed_at "
                + "from aml_transaction_risk where transaction_id=?", transactionId);
    }
    public List<ScoreResult> transactionHistory(UUID transactionId) {
        return many("select transaction_id subject_id,score,risk_level,model_version,explanation,source_data_mode,assessed_at "
                + "from aml_transaction_risk_history where transaction_id=? order by assessed_at desc", transactionId);
    }

    private ScoreResult saveCustomer(UUID id, UUID operator, List<Factor> factors, String mode) {
        BigDecimal score = score(factors); String level = level(score); String explanation = explanation(factors);
        Timestamp now = Timestamp.from(Instant.now());
        int updated = jdbc.update("update aml_customer_risk set score=?,risk_level=?,model_version=?,explanation=?,"
                        + "source_data_mode=?,calculated_by=?,updated_at=?,version=version+1 where customer_id=?",
                score, level, CUSTOMER_MODEL, explanation, mode, operator, now, id);
        if (updated == 0) jdbc.update("insert into aml_customer_risk(id,customer_id,score,risk_level,model_version,"
                        + "explanation,source_data_mode,calculated_by) values(?,?,?,?,?,?,?,?)",
                UUID.randomUUID(), id, score, level, CUSTOMER_MODEL, explanation, mode, operator);
        jdbc.update("insert into aml_customer_risk_history(id,customer_id,score,risk_level,model_version,explanation,"
                        + "source_data_mode,calculated_by,assessed_at) values(?,?,?,?,?,?,?,?,?)",
                UUID.randomUUID(), id, score, level, CUSTOMER_MODEL, explanation, mode, operator, now);
        audit.record("CALCULATE", "CUSTOMER_RISK", id, "SUCCESS",
                Map.of("score", score, "level", level, "modelVersion", CUSTOMER_MODEL, "sourceDataMode", mode));
        return new ScoreResult(id, score, level, CUSTOMER_MODEL, explanation, mode, now.toInstant());
    }

    private ScoreResult saveTransaction(UUID id, UUID operator, List<Factor> factors, String mode) {
        BigDecimal score = score(factors); String level = level(score); String explanation = explanation(factors);
        Timestamp now = Timestamp.from(Instant.now());
        int updated = jdbc.update("update aml_transaction_risk set score=?,risk_level=?,model_version=?,explanation=?,"
                        + "source_data_mode=?,calculated_by=?,updated_at=?,version=version+1 where transaction_id=?",
                score, level, TRANSACTION_MODEL, explanation, mode, operator, now, id);
        if (updated == 0) jdbc.update("insert into aml_transaction_risk(id,transaction_id,score,risk_level,model_version,"
                        + "explanation,source_data_mode,calculated_by) values(?,?,?,?,?,?,?,?)",
                UUID.randomUUID(), id, score, level, TRANSACTION_MODEL, explanation, mode, operator);
        jdbc.update("insert into aml_transaction_risk_history(id,transaction_id,score,risk_level,model_version,explanation,"
                        + "source_data_mode,calculated_by,assessed_at) values(?,?,?,?,?,?,?,?,?)",
                UUID.randomUUID(), id, score, level, TRANSACTION_MODEL, explanation, mode, operator, now);
        audit.record("CALCULATE", "TRANSACTION_RISK", id, "SUCCESS",
                Map.of("score", score, "level", level, "modelVersion", TRANSACTION_MODEL, "sourceDataMode", mode));
        return new ScoreResult(id, score, level, TRANSACTION_MODEL, explanation, mode, now.toInstant());
    }

    private Map<String, Long> screeningCounts(UUID customerId) {
        Map<String, Long> result = new HashMap<>();
        jdbc.query("select m.data_mode,m.review_status,count(*) total from aml_screening_match m "
                        + "join aml_screening_request r on r.id=m.request_id where r.customer_id=? "
                        + "group by m.data_mode,m.review_status",
                rs -> {
                    String status = rs.getString("review_status");
                    String group = "CONFIRMED_MATCH".equals(status) ? "CONFIRMED"
                            : ("PENDING".equals(status) || "NEEDS_MORE_INFO".equals(status)) ? "PENDING" : "IGNORED";
                    result.merge(rs.getString("data_mode") + "_" + group, rs.getLong("total"), Long::sum);
                }, customerId);
        return result;
    }

    private String dataMode(Map<String, Long> counts) {
        boolean real = counts.keySet().stream().anyMatch(key -> key.startsWith("REAL_") && !key.endsWith("_IGNORED"));
        boolean simulated = counts.keySet().stream().anyMatch(key -> key.startsWith("SIMULATED_") && !key.endsWith("_IGNORED"));
        return real && simulated ? "MIXED" : real ? "REAL" : simulated ? "SIMULATED" : "NONE";
    }

    private long count(String sql, UUID id) {
        Long value = jdbc.queryForObject(sql, Long.class, id);
        return value == null ? 0 : value;
    }
    private void add(List<Factor> factors, String code, long points, String reason) {
        factors.add(new Factor(code, Math.max(points, 0), reason));
    }
    private BigDecimal score(List<Factor> factors) {
        long total = factors.stream().mapToLong(Factor::points).sum();
        return BigDecimal.valueOf(Math.min(total, 100)).setScale(2, RoundingMode.HALF_UP);
    }
    private String level(BigDecimal score) {
        return score.compareTo(new BigDecimal("60")) >= 0 ? "HIGH"
                : score.compareTo(new BigDecimal("30")) >= 0 ? "MEDIUM" : "LOW";
    }
    private String explanation(List<Factor> factors) {
        try { return json.writeValueAsString(Map.of("factors", factors, "disclaimer",
                "确定性教学评分，仅用于优先级排序，不构成违法、制裁或监管结论")); }
        catch (JsonProcessingException exception) { throw new IllegalStateException("风险评分解释序列化失败", exception); }
    }
    private ScoreResult one(String sql, UUID id) {
        List<ScoreResult> rows = many(sql, id);
        if (rows.isEmpty()) throw new NoSuchElementException("风险评分不存在，请先执行计算");
        return rows.getFirst();
    }
    private List<ScoreResult> many(String sql, UUID id) {
        return jdbc.query(sql, (rs, rowNum) -> new ScoreResult(rs.getObject("subject_id", UUID.class),
                rs.getBigDecimal("score"), rs.getString("risk_level"), rs.getString("model_version"),
                rs.getString("explanation"), rs.getString("source_data_mode"),
                rs.getTimestamp("assessed_at").toInstant()), id);
    }

    public record Factor(String code, long points, String reason) {}
    public record ScoreResult(UUID subjectId, BigDecimal score, String riskLevel, String modelVersion,
                              String explanation, String sourceDataMode, Instant assessedAt) {}
}
