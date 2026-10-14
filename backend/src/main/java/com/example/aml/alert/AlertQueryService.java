package com.example.aml.alert;

import com.example.aml.alert.dto.AlertDetail;
import com.example.aml.alert.dto.AlertPage;
import com.example.aml.alert.dto.AlertSummary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

@Service
public class AlertQueryService {
    private final JdbcTemplate jdbc;

    public AlertQueryService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public AlertPage search(String status, String riskLevel, String ruleCode, String keyword, int page, int size) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), 200);
        StringBuilder where = new StringBuilder(" where 1=1");
        List<Object> args = new ArrayList<>();
        addExact(where, args, "a.status", status);
        addExact(where, args, "a.risk_level", riskLevel);
        addExact(where, args, "r.code", ruleCode);
        if (keyword != null && !keyword.isBlank()) {
            where.append(" and (lower(a.alert_no) like ? or lower(c.customer_no) like ? or lower(c.full_name) like ?)");
            String value = "%" + keyword.trim().toLowerCase() + "%";
            args.add(value);
            args.add(value);
            args.add(value);
        }
        String joins = " from aml_alert a join aml_rule_hit h on h.id=a.rule_hit_id "
                + "join aml_rule r on r.id=h.rule_id join aml_customer c on c.id=a.customer_id "
                + "left join sys_user u on u.id=a.assigned_to";
        Long total = jdbc.queryForObject("select count(*)" + joins + where, Long.class, args.toArray());
        List<Object> pageArgs = new ArrayList<>(args);
        pageArgs.add(safeSize);
        pageArgs.add(safePage * safeSize);
        String sql = "select a.id,a.alert_no,a.customer_id,c.customer_no,c.full_name customer_name,r.code rule_code,"
                + "h.rule_version,a.risk_level,a.status,a.assigned_to,u.username assigned_username,a.summary,a.created_at,"
                + "(select count(*) from aml_alert_transaction ax where ax.alert_id=a.id) transaction_count"
                + joins + where + " order by a.created_at desc limit ? offset ?";
        List<AlertSummary> content = jdbc.query(sql, (rs, rowNum) -> new AlertSummary(
                rs.getObject("id", UUID.class), rs.getString("alert_no"), rs.getObject("customer_id", UUID.class),
                rs.getString("customer_no"), rs.getString("customer_name"), rs.getString("rule_code"),
                rs.getInt("rule_version"), rs.getString("risk_level"), rs.getString("status"),
                rs.getObject("assigned_to", UUID.class), rs.getString("assigned_username"), rs.getString("summary"),
                rs.getLong("transaction_count"), instant(rs.getTimestamp("created_at"))), pageArgs.toArray());
        return new AlertPage(content, total == null ? 0 : total, safePage, safeSize);
    }

    public AlertDetail get(UUID id) {
        String sql = "select a.id,a.alert_no,a.customer_id,c.customer_no,c.full_name customer_name,r.code rule_code,"
                + "h.rule_version,a.risk_level,a.status,a.assigned_to,u.username assigned_username,a.summary,a.created_at,"
                + "h.hit_key,h.explanation,a.closed_reason,"
                + "(select count(*) from aml_alert_transaction ax where ax.alert_id=a.id) transaction_count "
                + "from aml_alert a join aml_rule_hit h on h.id=a.rule_hit_id join aml_rule r on r.id=h.rule_id "
                + "join aml_customer c on c.id=a.customer_id left join sys_user u on u.id=a.assigned_to where a.id=?";
        var rows = jdbc.query(sql, (rs, rowNum) -> {
            AlertSummary summary = new AlertSummary(rs.getObject("id", UUID.class), rs.getString("alert_no"),
                    rs.getObject("customer_id", UUID.class), rs.getString("customer_no"),
                    rs.getString("customer_name"), rs.getString("rule_code"), rs.getInt("rule_version"),
                    rs.getString("risk_level"), rs.getString("status"), rs.getObject("assigned_to", UUID.class),
                    rs.getString("assigned_username"), rs.getString("summary"), rs.getLong("transaction_count"),
                    instant(rs.getTimestamp("created_at")));
            return new AlertDetail(summary, rs.getString("hit_key"), rs.getString("explanation"),
                    rs.getString("closed_reason"), transactions(id), history(id));
        }, id);
        if (rows.isEmpty()) throw new NoSuchElementException("预警不存在");
        return rows.getFirst();
    }

    private List<AlertDetail.TransactionItem> transactions(UUID alertId) {
        return jdbc.query("select t.id,t.external_id transaction_no,t.amount,t.currency,t.direction,t.transaction_time "
                        + "from aml_alert_transaction ax join aml_transaction t on t.id=ax.transaction_id "
                        + "where ax.alert_id=? order by t.transaction_time",
                (rs, rowNum) -> new AlertDetail.TransactionItem(rs.getObject("id", UUID.class),
                        rs.getString("transaction_no"), rs.getBigDecimal("amount"), rs.getString("currency"),
                        rs.getString("direction"), instant(rs.getTimestamp("transaction_time"))), alertId);
    }

    private List<AlertDetail.HistoryItem> history(UUID alertId) {
        return jdbc.query("select h.id,h.from_status,h.to_status,h.operator_id,u.username,h.comment,h.created_at "
                        + "from aml_alert_history h left join sys_user u on u.id=h.operator_id "
                        + "where h.alert_id=? order by h.created_at",
                (rs, rowNum) -> new AlertDetail.HistoryItem(rs.getObject("id", UUID.class),
                        rs.getString("from_status"), rs.getString("to_status"), rs.getObject("operator_id", UUID.class),
                        rs.getString("username"), rs.getString("comment"), instant(rs.getTimestamp("created_at"))), alertId);
    }

    private void addExact(StringBuilder where, List<Object> args, String column, String value) {
        if (value != null && !value.isBlank()) {
            where.append(" and ").append(column).append("=?");
            args.add(value.trim().toUpperCase());
        }
    }

    private static Instant instant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }
}
