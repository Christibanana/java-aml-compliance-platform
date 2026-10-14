package com.example.aml.dashboard;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.*;
import java.util.*;

@Service
public class DashboardService {
    private final JdbcTemplate jdbc;

    public DashboardService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public DashboardData summary(ReportRange range) {
        Summary summary = new Summary(
                count("select count(*) from aml_customer"), count("select count(*) from aml_transaction"),
                count("select count(*) from aml_alert"),
                count("select count(*) from aml_alert where status in ('NEW','ASSIGNED','IN_REVIEW')"),
                count("select count(*) from aml_case"), count("select count(*) from aml_case where status<>'CLOSED'"),
                count("select count(*) from aml_customer c left join aml_customer_risk cr on cr.customer_id=c.id "
                        + "where coalesce(cr.risk_level,c.risk_level)='HIGH'")
        );
        List<Metric> riskDistribution = jdbc.query(
                "select coalesce(cr.risk_level,c.risk_level) metric_name,count(*) metric_value "
                        + "from aml_customer c left join aml_customer_risk cr on cr.customer_id=c.id "
                        + "group by coalesce(cr.risk_level,c.risk_level)",
                (rs, row) -> new Metric(rs.getString("metric_name"), rs.getLong("metric_value")));
        riskDistribution = ordered(riskDistribution, List.of("LOW", "MEDIUM", "HIGH"));

        Map<LocalDate, Long> alertCounts = new HashMap<>();
        jdbc.query("select cast(created_at as date) metric_day,count(*) metric_value from aml_alert "
                        + "where created_at>=? and created_at<? group by cast(created_at as date)",
                rs -> { alertCounts.put(rs.getDate("metric_day").toLocalDate(), rs.getLong("metric_value")); },
                Timestamp.from(range.startInclusive()), Timestamp.from(range.endExclusive()));
        List<TrendPoint> alertTrend = new ArrayList<>();
        for (LocalDate day = range.from(); !day.isAfter(range.to()); day = day.plusDays(1)) {
            alertTrend.add(new TrendPoint(day, alertCounts.getOrDefault(day, 0L)));
        }

        List<RuleHitMetric> ruleHits = jdbc.query(
                "select r.code,r.name,r.severity,count(h.id) metric_value from aml_rule r "
                        + "left join aml_rule_hit h on h.rule_id=r.id and h.created_at>=? and h.created_at<? "
                        + "group by r.code,r.name,r.severity order by r.code",
                (rs, row) -> new RuleHitMetric(rs.getString("code"), rs.getString("name"),
                        rs.getString("severity"), rs.getLong("metric_value")),
                Timestamp.from(range.startInclusive()), Timestamp.from(range.endExclusive()));
        List<Metric> caseStatuses = grouped("select status metric_name,count(*) metric_value from aml_case "
                + "where created_at>=? and created_at<? group by status", range,
                List.of("OPEN", "INVESTIGATING", "PENDING_REVIEW", "CLOSED"));
        List<Metric> casePriorities = grouped("select priority metric_name,count(*) metric_value from aml_case "
                + "where created_at>=? and created_at<? group by priority", range,
                List.of("LOW", "MEDIUM", "HIGH"));
        return new DashboardData(summary, riskDistribution, alertTrend, ruleHits, caseStatuses, casePriorities,
                range.from(), range.to(), Instant.now());
    }

    private List<Metric> grouped(String sql, ReportRange range, List<String> order) {
        List<Metric> rows = jdbc.query(sql, (rs, row) -> new Metric(rs.getString("metric_name"),
                rs.getLong("metric_value")), Timestamp.from(range.startInclusive()), Timestamp.from(range.endExclusive()));
        return ordered(rows, order);
    }

    private List<Metric> ordered(List<Metric> source, List<String> order) {
        Map<String, Long> values = new HashMap<>();
        source.forEach(metric -> values.put(metric.name(), metric.value()));
        List<Metric> result = new ArrayList<>();
        order.forEach(name -> result.add(new Metric(name, values.getOrDefault(name, 0L))));
        source.stream().filter(metric -> !order.contains(metric.name())).sorted(Comparator.comparing(Metric::name))
                .forEach(result::add);
        return List.copyOf(result);
    }

    private long count(String sql) {
        Long value = jdbc.queryForObject(sql, Long.class);
        return value == null ? 0 : value;
    }

    public record Summary(long customers, long transactions, long alerts, long pendingAlerts, long cases,
                          long activeCases, long highRiskCustomers) {}
    public record Metric(String name, long value) {}
    public record TrendPoint(LocalDate day, long value) {}
    public record RuleHitMetric(String code, String name, String severity, long value) {}
    public record DashboardData(Summary summary, List<Metric> customerRiskDistribution,
                                List<TrendPoint> alertTrend, List<RuleHitMetric> ruleHits,
                                List<Metric> caseStatuses, List<Metric> casePriorities,
                                LocalDate from, LocalDate to, Instant generatedAt) {}
}
