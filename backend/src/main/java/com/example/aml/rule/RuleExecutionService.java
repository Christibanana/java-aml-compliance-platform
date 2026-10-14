package com.example.aml.rule;

import com.example.aml.audit.AuditService;
import com.example.aml.rule.dto.ExecuteRulesRequest;
import com.fasterxml.jackson.databind.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class RuleExecutionService {
    private static final Set<String> SUPPORTED = Set.of("AML-R001", "AML-R002", "AML-R003");
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final AuditService audit;
    private final DroolsRuleEvaluator drools;

    public RuleExecutionService(JdbcTemplate jdbc, ObjectMapper json, AuditService audit, DroolsRuleEvaluator drools) {
        this.jdbc = jdbc;
        this.json = json;
        this.audit = audit;
        this.drools = drools;
    }

    public record RuleResult(String code, int version, int candidates, int newHits, int alerts) {}
    public record Result(UUID executionId, int processed, int hits, int alerts, String note, List<RuleResult> rules) {}
    private record RuleConfig(UUID id, String code, int version, String severity, JsonNode parameters) {}
    private record TransactionRow(UUID transactionId, UUID accountId, UUID customerId,
                                  BigDecimal amount, Instant transactionTime) {}
    private record AccountCustomerKey(UUID accountId, UUID customerId) {}

    @Transactional
    public Result execute(ExecuteRulesRequest request) {
        Instant asOf = request == null || request.asOf() == null ? Instant.now() : request.asOf();
        Set<String> requested = request == null || request.ruleCodes() == null || request.ruleCodes().isEmpty()
                ? SUPPORTED : new TreeSet<>(request.ruleCodes());
        if (!SUPPORTED.containsAll(requested)) {
            throw new IllegalArgumentException("当前仅支持 AML-R001、AML-R002、AML-R003；复杂图规则标记为后续实现");
        }
        List<RuleConfig> configs = loadConfigs(requested);
        if (configs.isEmpty()) throw new IllegalArgumentException("没有可执行的已启用规则");
        UUID executionId = UUID.randomUUID();
        Instant asOfWindowStart = earliestWindow(configs, asOf);
        String scope = configs.stream().map(RuleConfig::code).sorted().collect(Collectors.joining(","));
        jdbc.update("insert into aml_rule_execution(id,execution_key,scope,started_at,status,window_start,window_end) " +
                        "values(?,?,?,?,?,?,?)", executionId, "RULE-RUN-" + UUID.randomUUID(), scope,
                Timestamp.from(Instant.now()), "RUNNING", Timestamp.from(asOfWindowStart), Timestamp.from(asOf));

        List<TransactionRow> allRows = loadTransactions(asOf);
        List<RuleResult> results = new ArrayList<>();
        int totalHits = 0, totalAlerts = 0;
        for (RuleConfig config : configs) {
            jdbc.queryForObject("select id from aml_rule where id=? for update", UUID.class, config.id());
            List<RuleEvaluationFact> facts = buildFacts(config, allRows, asOf);
            drools.evaluate(facts);
            int newHits = 0, alerts = 0;
            for (RuleEvaluationFact fact : facts) {
                if (!fact.isMatched()) continue;
                UUID hitId = persistHit(executionId, config, fact);
                if (hitId != null) {
                    newHits++;
                    createAlert(hitId, config, fact);
                    alerts++;
                }
            }
            results.add(new RuleResult(config.code(), config.version(), facts.size(), newHits, alerts));
            totalHits += newHits;
            totalAlerts += alerts;
        }
        int processed = (int) allRows.stream().map(TransactionRow::transactionId).distinct().count();
        jdbc.update("update aml_rule_execution set status='COMPLETED',finished_at=?,processed_count=?,hit_count=? where id=?",
                Timestamp.from(Instant.now()), processed, totalHits, executionId);
        audit.record("EXECUTE", "RULE", executionId, "SUCCESS",
                Map.of("rules", scope, "processed", processed, "newHits", totalHits));
        return new Result(executionId, processed, totalHits, totalAlerts,
                "Drools " + drools.runtimeVersion() + "；固定受控模板执行，阈值仅用于教学演示", List.copyOf(results));
    }

    private List<RuleConfig> loadConfigs(Set<String> requested) {
        String placeholders = String.join(",", Collections.nCopies(requested.size(), "?"));
        return jdbc.query("select r.id,r.code,r.active_version,r.severity,v.parameters_json from aml_rule r " +
                        "join aml_rule_version v on v.rule_id=r.id and v.version_no=r.active_version " +
                        "where r.enabled=true and r.implementation_status='IMPLEMENTED' and r.code in (" + placeholders + ") " +
                        "order by r.code", (rs, rowNum) -> {
                    try {
                        return new RuleConfig(rs.getObject("id", UUID.class), rs.getString("code"),
                                rs.getInt("active_version"), rs.getString("severity"),
                                json.readTree(rs.getString("parameters_json")));
                    } catch (Exception exception) {
                        throw new IllegalStateException("规则参数格式错误", exception);
                    }
                }, requested.toArray());
    }

    private List<TransactionRow> loadTransactions(Instant asOf) {
        return jdbc.query("select t.id,t.account_id,ac.customer_id,t.amount,t.transaction_time " +
                        "from aml_transaction t join aml_account_customer ac on ac.account_id=t.account_id " +
                        "where t.status='COMPLETED' and t.transaction_time<=? order by t.transaction_time,t.id",
                (rs, rowNum) -> new TransactionRow(rs.getObject("id", UUID.class),
                        rs.getObject("account_id", UUID.class), rs.getObject("customer_id", UUID.class),
                        rs.getBigDecimal("amount"), rs.getTimestamp("transaction_time").toInstant()), Timestamp.from(asOf));
    }

    private List<RuleEvaluationFact> buildFacts(RuleConfig config, List<TransactionRow> rows, Instant asOf) {
        return switch (config.code()) {
            case "AML-R001" -> largeTransactionFacts(config, rows);
            case "AML-R002" -> windowFacts(config, rows, asOf, true);
            case "AML-R003" -> windowFacts(config, rows, asOf, false);
            default -> List.of();
        };
    }

    private List<RuleEvaluationFact> largeTransactionFacts(RuleConfig config, List<TransactionRow> rows) {
        BigDecimal threshold = positiveDecimal(config.parameters(), "amountThreshold");
        return rows.stream().map(row -> new RuleEvaluationFact(config.code(),
                row.customerId() + ":" + row.transactionId(), row.customerId(), row.accountId(),
                List.of(row.transactionId()), row.amount(), threshold, row.transactionTime(), row.transactionTime(),
                "单笔交易金额 " + row.amount() + "，阈值 " + threshold)).toList();
    }

    private List<RuleEvaluationFact> windowFacts(RuleConfig config, List<TransactionRow> rows,
                                                  Instant asOf, boolean frequency) {
        int window = positiveInteger(config.parameters(), frequency ? "windowMinutes" : "windowHours");
        Instant start = frequency ? asOf.minus(Duration.ofMinutes(window)) : asOf.minus(Duration.ofHours(window));
        BigDecimal threshold = frequency ? BigDecimal.valueOf(positiveInteger(config.parameters(), "countThreshold"))
                : positiveDecimal(config.parameters(), "amountThreshold");
        Map<AccountCustomerKey, List<TransactionRow>> groups = rows.stream()
                .filter(row -> !row.transactionTime().isBefore(start))
                .collect(Collectors.groupingBy(row -> new AccountCustomerKey(row.accountId(), row.customerId())));
        List<RuleEvaluationFact> facts = new ArrayList<>();
        groups.forEach((key, transactions) -> {
            List<UUID> ids = transactions.stream().map(TransactionRow::transactionId).sorted().toList();
            BigDecimal metric = frequency ? BigDecimal.valueOf(ids.size())
                    : transactions.stream().map(TransactionRow::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
            String label = frequency ? "窗口交易笔数 " : "窗口累计金额 ";
            facts.add(new RuleEvaluationFact(config.code(), key.customerId() + ":" + key.accountId() + ":" +
                    UUID.nameUUIDFromBytes(ids.toString().getBytes(StandardCharsets.UTF_8)), key.customerId(),
                    key.accountId(), ids, metric, threshold, start, asOf, label + metric + "，阈值 " + threshold));
        });
        return facts;
    }

    private UUID persistHit(UUID executionId, RuleConfig config, RuleEvaluationFact fact) {
        String hitKey = config.code() + ":v" + config.version() + ":" + fact.getSubjectKey();
        UUID hitId = UUID.randomUUID();
        UUID primaryTransaction = fact.getTransactionIds().size() == 1 ? fact.getTransactionIds().getFirst() : null;
        int inserted = jdbc.update("insert into aml_rule_hit(id,execution_id,rule_id,rule_version,transaction_id," +
                        "customer_id,hit_key,score,explanation) select ?,?,?,?,?,?,?,?,? " +
                        "where not exists(select 1 from aml_rule_hit where hit_key=?)", hitId, executionId,
                config.id(), config.version(), primaryTransaction, fact.getCustomerId(), hitKey,
                score(config.severity()), fact.getExplanation(), hitKey);
        if (inserted == 0) return null;
        for (UUID transactionId : fact.getTransactionIds()) {
            jdbc.update("insert into aml_rule_hit_transaction(rule_hit_id,transaction_id) values(?,?)", hitId, transactionId);
        }
        return hitId;
    }

    private void createAlert(UUID hitId, RuleConfig config, RuleEvaluationFact fact) {
        UUID alertId = UUID.randomUUID();
        String alertNo = "ALT-" + UUID.randomUUID().toString().substring(0, 13).toUpperCase();
        String summary = config.code() + " " + fact.getExplanation();
        jdbc.update("insert into aml_alert(id,alert_no,rule_hit_id,customer_id,risk_level,status,summary) " +
                        "values(?,?,?,?,?,?,?)", alertId, alertNo, hitId, fact.getCustomerId(), config.severity(),
                "NEW", summary.length() > 500 ? summary.substring(0, 500) : summary);
        for (UUID transactionId : fact.getTransactionIds()) {
            jdbc.update("insert into aml_alert_transaction(alert_id,transaction_id) values(?,?)", alertId, transactionId);
        }
        jdbc.update("insert into aml_alert_history(id,alert_id,to_status,comment) values(?,?,?,?)",
                UUID.randomUUID(), alertId, "NEW", "规则 " + config.code() + " v" + config.version() + " 自动创建");
    }

    private Instant earliestWindow(List<RuleConfig> configs, Instant asOf) {
        Instant earliest = asOf;
        for (RuleConfig config : configs) {
            if (config.code().equals("AML-R002")) earliest = min(earliest,
                    asOf.minus(Duration.ofMinutes(positiveInteger(config.parameters(), "windowMinutes"))));
            if (config.code().equals("AML-R003")) earliest = min(earliest,
                    asOf.minus(Duration.ofHours(positiveInteger(config.parameters(), "windowHours"))));
        }
        return earliest;
    }
    private static Instant min(Instant left, Instant right) { return left.isBefore(right) ? left : right; }
    private static BigDecimal positiveDecimal(JsonNode node, String field) {
        BigDecimal value = node.path(field).decimalValue();
        if (value.signum() <= 0) throw new IllegalStateException("规则参数 " + field + " 必须大于 0");
        return value;
    }
    private static int positiveInteger(JsonNode node, String field) {
        int value = node.path(field).intValue();
        if (value <= 0) throw new IllegalStateException("规则参数 " + field + " 必须大于 0");
        return value;
    }
    private static BigDecimal score(String severity) {
        return switch (severity) {
            case "HIGH" -> new BigDecimal("80");
            case "MEDIUM" -> new BigDecimal("60");
            default -> new BigDecimal("40");
        };
    }
}
