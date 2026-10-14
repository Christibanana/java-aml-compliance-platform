package com.example.aml.dashboard;

import com.example.aml.audit.AuditService;
import com.example.aml.common.CsvCell;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.util.*;

@Service
public class ReportExportService {
    public enum ReportType { CUSTOMER_RISK, ALERTS, RULE_HITS, CASES }

    private final JdbcTemplate jdbc;
    private final AuditService audit;

    public ReportExportService(JdbcTemplate jdbc, AuditService audit) {
        this.jdbc = jdbc;
        this.audit = audit;
    }

    public ExportedReport export(ReportType type, ReportRange range) {
        StringBuilder csv = new StringBuilder("\uFEFF");
        int rows = switch (type) {
            case CUSTOMER_RISK -> customerRisk(csv);
            case ALERTS -> alerts(csv, range);
            case RULE_HITS -> ruleHits(csv, range);
            case CASES -> cases(csv, range);
        };
        audit.record("EXPORT", "REPORT", type, "SUCCESS",
                Map.of("type", type.name(), "from", range.from(), "to", range.to(), "rows", rows));
        String filename = "aml-" + type.name().toLowerCase(Locale.ROOT).replace('_', '-') + "-"
                + range.from() + "-" + range.to() + ".csv";
        return new ExportedReport(filename, csv.toString().getBytes(StandardCharsets.UTF_8), rows);
    }

    private int customerRisk(StringBuilder csv) {
        row(csv, "客户编号", "客户名称", "客户状态", "风险等级", "风险分数", "模型版本", "数据来源", "评分时间");
        List<Map<String, Object>> rows = jdbc.queryForList("select c.customer_no,c.full_name,c.status,"
                + "coalesce(cr.risk_level,c.risk_level) risk_level,cr.score,cr.model_version,"
                + "coalesce(cr.source_data_mode,'NONE') source_data_mode,cr.updated_at assessed_at "
                + "from aml_customer c left join aml_customer_risk cr on cr.customer_id=c.id order by c.customer_no");
        rows.forEach(item -> row(csv, values(item, "customer_no", "full_name", "status", "risk_level", "score",
                "model_version", "source_data_mode", "assessed_at")));
        return rows.size();
    }

    private int alerts(StringBuilder csv, ReportRange range) {
        row(csv, "预警编号", "风险等级", "状态", "客户编号", "规则编码", "摘要", "创建时间");
        List<Map<String, Object>> rows = jdbc.queryForList("select a.alert_no,a.risk_level,a.status,c.customer_no,"
                + "r.code rule_code,a.summary,a.created_at from aml_alert a "
                + "left join aml_customer c on c.id=a.customer_id join aml_rule_hit h on h.id=a.rule_hit_id "
                + "join aml_rule r on r.id=h.rule_id where a.created_at>=? and a.created_at<? order by a.created_at desc",
                Timestamp.from(range.startInclusive()), Timestamp.from(range.endExclusive()));
        rows.forEach(item -> row(csv, values(item, "alert_no", "risk_level", "status", "customer_no", "rule_code",
                "summary", "created_at")));
        return rows.size();
    }

    private int ruleHits(StringBuilder csv, ReportRange range) {
        row(csv, "规则编码", "规则名称", "严重程度", "命中数量", "涉及客户数", "涉及交易数");
        List<Map<String, Object>> rows = jdbc.queryForList("select r.code,r.name,r.severity,count(h.id) hit_count,"
                + "count(distinct h.customer_id) customer_count,count(distinct rht.transaction_id) transaction_count "
                + "from aml_rule r left join aml_rule_hit h on h.rule_id=r.id and h.created_at>=? and h.created_at<? "
                + "left join aml_rule_hit_transaction rht on rht.rule_hit_id=h.id "
                + "group by r.code,r.name,r.severity order by r.code",
                Timestamp.from(range.startInclusive()), Timestamp.from(range.endExclusive()));
        rows.forEach(item -> row(csv, values(item, "code", "name", "severity", "hit_count", "customer_count",
                "transaction_count")));
        return rows.size();
    }

    private int cases(StringBuilder csv, ReportRange range) {
        row(csv, "案件编号", "标题", "状态", "优先级", "调查员", "关联预警数", "创建时间", "关闭时间");
        List<Map<String, Object>> rows = jdbc.queryForList("select c.case_no,c.title,c.status,c.priority,u.username,"
                + "count(ca.alert_id) alert_count,c.created_at,c.closed_at from aml_case c "
                + "left join sys_user u on u.id=c.investigator_id left join aml_case_alert ca on ca.case_id=c.id "
                + "where c.created_at>=? and c.created_at<? group by c.id,c.case_no,c.title,c.status,c.priority,"
                + "u.username,c.created_at,c.closed_at order by c.created_at desc",
                Timestamp.from(range.startInclusive()), Timestamp.from(range.endExclusive()));
        rows.forEach(item -> row(csv, values(item, "case_no", "title", "status", "priority", "username", "alert_count",
                "created_at", "closed_at")));
        return rows.size();
    }

    private Object[] values(Map<String, Object> row, String... keys) {
        Object[] values = new Object[keys.length];
        for (int index = 0; index < keys.length; index++) values[index] = row.get(keys[index]);
        return values;
    }

    private void row(StringBuilder csv, Object... values) {
        for (int index = 0; index < values.length; index++) {
            if (index > 0) csv.append(',');
            csv.append(CsvCell.quoted(values[index]));
        }
        csv.append("\r\n");
    }

    public record ExportedReport(String filename, byte[] content, int rows) {}
}
