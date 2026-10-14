package com.example.aml.audit;

import com.example.aml.dashboard.ReportRange;
import com.example.aml.common.CsvCell;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;

@Service
public class AuditQueryService {
    private static final int EXPORT_LIMIT = 10_000;
    private final JdbcTemplate jdbc;
    private final AuditService audit;

    public AuditQueryService(JdbcTemplate jdbc, AuditService audit) {
        this.jdbc = jdbc;
        this.audit = audit;
    }

    public AuditPage query(AuditFilter filter) {
        SqlFilter sql = where(filter);
        Long total = jdbc.queryForObject("select count(*) from sys_audit_log" + sql.where(), Long.class,
                sql.parameters().toArray());
        List<Object> parameters = new ArrayList<>(sql.parameters());
        parameters.add(filter.size());
        parameters.add((long) filter.page() * filter.size());
        List<AuditItem> content = jdbc.query("select id,actor,action,resource_type,resource_id,result,client_ip,"
                        + "details_json,occurred_at from sys_audit_log" + sql.where()
                        + " order by occurred_at desc limit ? offset ?",
                (rs, row) -> new AuditItem(rs.getObject("id", UUID.class), rs.getString("actor"),
                        rs.getString("action"), rs.getString("resource_type"), rs.getString("resource_id"),
                        rs.getString("result"), rs.getString("client_ip"), rs.getString("details_json"),
                        rs.getTimestamp("occurred_at").toInstant()), parameters.toArray());
        long resolvedTotal = total == null ? 0 : total;
        return new AuditPage(content, resolvedTotal, filter.page(), filter.size(),
                (int) Math.ceil((double) resolvedTotal / filter.size()));
    }

    public AuditExport export(AuditFilter filter) {
        SqlFilter sql = where(filter);
        List<Object> parameters = new ArrayList<>(sql.parameters());
        parameters.add(EXPORT_LIMIT);
        List<AuditItem> rows = jdbc.query("select id,actor,action,resource_type,resource_id,result,client_ip,"
                        + "details_json,occurred_at from sys_audit_log" + sql.where()
                        + " order by occurred_at desc limit ?",
                (rs, row) -> new AuditItem(rs.getObject("id", UUID.class), rs.getString("actor"),
                        rs.getString("action"), rs.getString("resource_type"), rs.getString("resource_id"),
                        rs.getString("result"), rs.getString("client_ip"), rs.getString("details_json"),
                        rs.getTimestamp("occurred_at").toInstant()), parameters.toArray());
        StringBuilder csv = new StringBuilder("\uFEFF\"时间\",\"操作者\",\"动作\",\"资源类型\",\"资源ID\",\"结果\",\"客户端IP\",\"详情JSON\"\r\n");
        rows.forEach(item -> csvRow(csv, item.occurredAt(), item.actor(), item.action(), item.resourceType(),
                item.resourceId(), item.result(), item.clientIp(), item.detailsJson()));
        audit.record("EXPORT", "AUDIT_LOG", null, "SUCCESS",
                Map.of("from", filter.range().from(), "to", filter.range().to(), "rows", rows.size()));
        return new AuditExport("aml-audit-" + filter.range().from() + "-" + filter.range().to() + ".csv",
                csv.toString().getBytes(StandardCharsets.UTF_8), rows.size());
    }

    private SqlFilter where(AuditFilter filter) {
        StringBuilder sql = new StringBuilder(" where occurred_at>=? and occurred_at<?");
        List<Object> parameters = new ArrayList<>();
        parameters.add(Timestamp.from(filter.range().startInclusive()));
        parameters.add(Timestamp.from(filter.range().endExclusive()));
        exact(sql, parameters, "action", filter.action());
        exact(sql, parameters, "resource_type", filter.resourceType());
        exact(sql, parameters, "result", filter.result());
        if (hasText(filter.actor())) {
            sql.append(" and lower(actor) like ?");
            parameters.add("%" + filter.actor().trim().toLowerCase(Locale.ROOT) + "%");
        }
        return new SqlFilter(sql.toString(), List.copyOf(parameters));
    }

    private void exact(StringBuilder sql, List<Object> parameters, String column, String value) {
        if (hasText(value)) {
            sql.append(" and ").append(column).append("=?");
            parameters.add(value.trim().toUpperCase(Locale.ROOT));
        }
    }

    private boolean hasText(String value) { return value != null && !value.isBlank(); }

    private void csvRow(StringBuilder csv, Object... values) {
        for (int index = 0; index < values.length; index++) {
            if (index > 0) csv.append(',');
            csv.append(CsvCell.quoted(values[index]));
        }
        csv.append("\r\n");
    }

    private record SqlFilter(String where, List<Object> parameters) {}
    public record AuditFilter(String actor, String action, String resourceType, String result,
                              ReportRange range, int page, int size) {}
    public record AuditItem(UUID id, String actor, String action, String resourceType, String resourceId,
                            String result, String clientIp, String detailsJson, Instant occurredAt) {}
    public record AuditPage(List<AuditItem> content, long total, int page, int size, int totalPages) {}
    public record AuditExport(String filename, byte[] content, int rows) {}
}
