package com.example.aml.screening;

import com.example.aml.audit.AuditService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;

@Service
public class ScreeningService {
    public enum ReviewDecision { NEEDS_MORE_INFO, CONFIRMED_MATCH, FALSE_POSITIVE }

    private final JdbcTemplate jdbc;
    private final ScreeningProviderRegistry providers;
    private final TransactionTemplate transactions;
    private final AuditService audit;

    public ScreeningService(JdbcTemplate jdbc, ScreeningProviderRegistry providers,
                            TransactionTemplate transactions, AuditService audit) {
        this.jdbc = jdbc;
        this.providers = providers;
        this.transactions = transactions;
        this.audit = audit;
    }

    public ScreeningDetail screen(UUID customerId, String name, String countryCode, String schema,
                                  String providerName, UUID operatorId) {
        if (customerId != null) {
            Integer count = jdbc.queryForObject("select count(*) from aml_customer where id=?", Integer.class, customerId);
            if (count == null || count == 0) throw new NoSuchElementException("客户不存在");
        }
        ScreeningProvider provider = providers.require(providerName);
        UUID requestId = UUID.randomUUID();
        jdbc.update("insert into aml_screening_request(id,customer_id,query_name,provider,provider_mode,status,requested_by) "
                        + "values(?,?,?,?,?,'PENDING',?)", requestId, customerId, name.trim(), provider.name(),
                provider.dataMode().name(), operatorId);
        try {
            List<ScreeningProvider.Match> matches = provider.search(new ScreeningProvider.Query(
                    name.trim(), normalizeCountry(countryCode), schema));
            transactions.executeWithoutResult(status -> {
                for (ScreeningProvider.Match match : matches) {
                    jdbc.update("insert into aml_screening_match(id,request_id,external_entity_id,matched_name,score,"
                                    + "dataset,payload_json,data_mode,review_status) values(?,?,?,?,?,?,?,?, 'PENDING')",
                            UUID.randomUUID(), requestId, match.externalId(), match.matchedName(), match.score(),
                            match.dataset(), match.payloadJson(), provider.dataMode().name());
                }
                Timestamp now = Timestamp.from(Instant.now());
                jdbc.update("update aml_screening_request set status='COMPLETED',completed_at=?,updated_at=? where id=?",
                        now, now, requestId);
                audit.record("SCREEN", "SCREENING_REQUEST", requestId, "SUCCESS",
                        auditDetails(customerId, provider, matches.size(), null));
            });
            return get(requestId);
        } catch (RuntimeException exception) {
            String message = safeError(exception.getMessage());
            Timestamp now = Timestamp.from(Instant.now());
            jdbc.update("update aml_screening_request set status='FAILED',error_message=?,completed_at=?,updated_at=? where id=?",
                    message, now, now, requestId);
            audit.record("SCREEN", "SCREENING_REQUEST", requestId, "FAILED",
                    auditDetails(customerId, provider, 0, message));
            throw new ScreeningProviderException(requestId, message, exception);
        }
    }

    public List<ScreeningSummary> list() {
        return jdbc.query(summarySql(" order by r.created_at desc limit 200"),
                (rs, rowNum) -> new ScreeningSummary(rs.getObject("id", UUID.class),
                        rs.getObject("customer_id", UUID.class), rs.getString("query_name"), rs.getString("provider"),
                        rs.getString("provider_mode"), rs.getString("status"), rs.getString("error_message"),
                        instant(rs.getTimestamp("completed_at")), instant(rs.getTimestamp("created_at")),
                        rs.getLong("match_count"), rs.getLong("pending_count")));
    }

    public ScreeningDetail get(UUID requestId) {
        List<ScreeningSummary> requests = jdbc.query(summarySql(" where r.id=?"),
                (rs, rowNum) -> new ScreeningSummary(rs.getObject("id", UUID.class),
                        rs.getObject("customer_id", UUID.class), rs.getString("query_name"), rs.getString("provider"),
                        rs.getString("provider_mode"), rs.getString("status"), rs.getString("error_message"),
                        instant(rs.getTimestamp("completed_at")), instant(rs.getTimestamp("created_at")),
                        rs.getLong("match_count"), rs.getLong("pending_count")), requestId);
        if (requests.isEmpty()) throw new NoSuchElementException("筛查请求不存在");
        List<MatchItem> matches = jdbc.query("select id,external_entity_id,matched_name,score,dataset,data_mode,"
                        + "review_status,created_at from aml_screening_match where request_id=? order by score desc",
                (rs, rowNum) -> new MatchItem(rs.getObject("id", UUID.class), rs.getString("external_entity_id"),
                        rs.getString("matched_name"), rs.getBigDecimal("score"), rs.getString("dataset"),
                        rs.getString("data_mode"), rs.getString("review_status"),
                        instant(rs.getTimestamp("created_at")), reviews(rs.getObject("id", UUID.class))), requestId);
        return new ScreeningDetail(requests.getFirst(), matches,
                "SIMULATED".equals(requests.getFirst().dataMode())
                        ? "模拟数据：仅用于测试和演示，绝不代表真实名单命中。"
                        : "真实 Provider 数据：候选仍必须人工复核，匹配不等于违法或制裁结论。");
    }

    @Transactional
    public ScreeningDetail review(UUID requestId, UUID matchId, ReviewDecision decision, String comment, UUID reviewerId) {
        List<String> states = jdbc.query("select m.review_status from aml_screening_match m "
                        + "join aml_screening_request r on r.id=m.request_id where m.id=? and r.id=? for update",
                (rs, rowNum) -> rs.getString(1), matchId, requestId);
        if (states.isEmpty()) throw new NoSuchElementException("筛查候选不存在");
        String current = states.getFirst();
        if ("CONFIRMED_MATCH".equals(current) || "FALSE_POSITIVE".equals(current)) {
            throw new IllegalArgumentException("已完成最终复核的候选不能再次修改");
        }
        jdbc.update("insert into aml_screening_review(id,match_id,decision,comment,reviewer_id) values(?,?,?,?,?)",
                UUID.randomUUID(), matchId, decision.name(), comment.trim(), reviewerId);
        jdbc.update("update aml_screening_match set review_status=?,updated_at=? where id=?",
                decision.name(), Timestamp.from(Instant.now()), matchId);
        audit.record("REVIEW", "SCREENING_MATCH", matchId, "SUCCESS",
                Map.of("requestId", requestId, "from", current, "to", decision.name()));
        return get(requestId);
    }

    public List<ScreeningProviderRegistry.ProviderInfo> providerInfo() { return providers.list(); }

    private List<ReviewItem> reviews(UUID matchId) {
        return jdbc.query("select r.id,r.decision,r.comment,r.reviewer_id,u.username,r.reviewed_at "
                        + "from aml_screening_review r left join sys_user u on u.id=r.reviewer_id "
                        + "where r.match_id=? order by r.reviewed_at",
                (rs, rowNum) -> new ReviewItem(rs.getObject("id", UUID.class), rs.getString("decision"),
                        rs.getString("comment"), rs.getObject("reviewer_id", UUID.class), rs.getString("username"),
                        instant(rs.getTimestamp("reviewed_at"))), matchId);
    }

    private String summarySql(String suffix) {
        return "select r.id,r.customer_id,r.query_name,r.provider,r.provider_mode,r.status,r.error_message,"
                + "r.completed_at,r.created_at,(select count(*) from aml_screening_match m where m.request_id=r.id) match_count,"
                + "(select count(*) from aml_screening_match m where m.request_id=r.id and m.review_status in ('PENDING','NEEDS_MORE_INFO')) pending_count "
                + "from aml_screening_request r" + suffix;
    }

    private Map<String, Object> auditDetails(UUID customerId, ScreeningProvider provider, int matches, String error) {
        Map<String, Object> details = new LinkedHashMap<>();
        if (customerId != null) details.put("customerId", customerId);
        details.put("provider", provider.name());
        details.put("dataMode", provider.dataMode().name());
        details.put("matches", matches);
        if (error != null) details.put("error", error);
        return details;
    }

    private String normalizeCountry(String value) {
        return value == null || value.isBlank() ? null : value.trim().toUpperCase(Locale.ROOT);
    }
    private String safeError(String value) {
        String message = value == null || value.isBlank() ? "外部筛查服务失败" : value;
        return message.length() > 1000 ? message.substring(0, 1000) : message;
    }
    private Instant instant(Timestamp value) { return value == null ? null : value.toInstant(); }

    public record ScreeningSummary(UUID id, UUID customerId, String queryName, String provider, String dataMode,
            String status, String errorMessage, Instant completedAt, Instant createdAt, long matchCount,
            long pendingReviewCount) {}
    public record ScreeningDetail(ScreeningSummary request, List<MatchItem> matches, String warning) {}
    public record MatchItem(UUID id, String externalId, String matchedName, BigDecimal score, String dataset,
            String dataMode, String reviewStatus, Instant createdAt, List<ReviewItem> reviews) {}
    public record ReviewItem(UUID id, String decision, String comment, UUID reviewerId, String reviewerUsername,
            Instant reviewedAt) {}
}
