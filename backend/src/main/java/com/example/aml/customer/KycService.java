package com.example.aml.customer;

import com.example.aml.audit.AuditService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;

@Service
public class KycService {
    public record KycRecord(UUID id, UUID customerId, String status, String documentSummary,
                            UUID submittedBy, Instant submittedAt, UUID reviewedBy, Instant reviewedAt,
                            String reviewReason, Instant expiresAt) {}
    public record KycEvent(String fromStatus, String toStatus, UUID operatorId, String reason, Instant occurredAt) {}
    public record KycDetail(KycRecord current, List<KycEvent> history) {}

    private final JdbcTemplate jdbc;
    private final AuditService audit;

    public KycService(JdbcTemplate jdbc, AuditService audit) {
        this.jdbc = jdbc;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public KycDetail get(UUID customerId) {
        requireCustomer(customerId, false);
        return detail(customerId);
    }

    @Transactional
    public KycDetail submit(UUID customerId, String summary) {
        requireCustomer(customerId, true);
        UUID actor = actorId();
        KycRecord previous = latest(customerId);
        Instant now = Instant.now();
        if (previous != null) {
            if (previous.status().equals("PENDING")) throw new IllegalArgumentException("已有待审核的 KYC 记录");
            if (previous.status().equals("VERIFIED")) {
                if (previous.expiresAt() == null || previous.expiresAt().isAfter(now)) {
                    throw new IllegalArgumentException("当前 KYC 尚未到期");
                }
                jdbc.update("update aml_customer_kyc set status='EXPIRED',updated_at=? where id=?",
                        Timestamp.from(now), previous.id());
                event(previous.id(), customerId, "VERIFIED", "EXPIRED", actor, "有效期届满", now);
            }
        }
        UUID id = UUID.randomUUID();
        jdbc.update("insert into aml_customer_kyc(id,customer_id,status,document_summary,source,submitted_by,submitted_at) "
                        + "values(?,?,'PENDING',?,'SYNTHETIC',?,?)",
                id, customerId, summary.trim(), actor, Timestamp.from(now));
        event(id, customerId, null, "PENDING", actor, "提交资料摘要", now);
        audit.record("SUBMIT", "CUSTOMER_KYC", id, "SUCCESS", Map.of("customerId", customerId.toString()));
        return detail(customerId);
    }

    @Transactional
    public KycDetail review(UUID customerId, String decision, String reason, Instant expiresAt) {
        requireCustomer(customerId, true);
        KycRecord current = latest(customerId);
        if (current == null) throw new NoSuchElementException("客户尚无 KYC 记录");
        if (!current.status().equals("PENDING")) throw new IllegalArgumentException("仅待审核记录可审核");
        if (!decision.equals("VERIFIED") && !decision.equals("REJECTED")) {
            throw new IllegalArgumentException("审核结论仅支持 VERIFIED 或 REJECTED");
        }
        Instant now = Instant.now();
        if (decision.equals("VERIFIED") && (expiresAt == null || !expiresAt.isAfter(now))) {
            throw new IllegalArgumentException("审核通过时须设置未来的到期时间");
        }
        if (decision.equals("REJECTED") && expiresAt != null) {
            throw new IllegalArgumentException("退回审核不得设置到期时间");
        }
        UUID actor = actorId();
        jdbc.update("update aml_customer_kyc set status=?,reviewed_by=?,reviewed_at=?,review_reason=?,expires_at=?," 
                        + "verified_at=?,updated_at=? where id=?",
                decision, actor, Timestamp.from(now), reason.trim(),
                expiresAt == null ? null : Timestamp.from(expiresAt),
                decision.equals("VERIFIED") ? Timestamp.from(now) : null, Timestamp.from(now), current.id());
        event(current.id(), customerId, "PENDING", decision, actor, reason.trim(), now);
        audit.record("REVIEW", "CUSTOMER_KYC", current.id(), "SUCCESS",
                Map.of("customerId", customerId.toString(), "decision", decision));
        return detail(customerId);
    }

    private KycDetail detail(UUID customerId) {
        KycRecord current = latest(customerId);
        if (current != null && current.status().equals("VERIFIED") && current.expiresAt() != null
                && !current.expiresAt().isAfter(Instant.now())) {
            current = new KycRecord(current.id(), current.customerId(), "EXPIRED", current.documentSummary(),
                    current.submittedBy(), current.submittedAt(), current.reviewedBy(), current.reviewedAt(),
                    current.reviewReason(), current.expiresAt());
        }
        List<KycEvent> history = jdbc.query("select from_status,to_status,operator_id,reason,occurred_at "
                        + "from aml_customer_kyc_history where customer_id=? order by occurred_at desc,id desc",
                (rs, row) -> new KycEvent(rs.getString(1), rs.getString(2),
                        rs.getObject(3, UUID.class), rs.getString(4), rs.getTimestamp(5).toInstant()), customerId);
        return new KycDetail(current, history);
    }

    private KycRecord latest(UUID customerId) {
        return jdbc.query("select id,customer_id,status,document_summary,submitted_by,submitted_at,reviewed_by," 
                        + "reviewed_at,review_reason,expires_at from aml_customer_kyc "
                        + "where customer_id=? order by created_at desc,id desc limit 1",
                (rs, row) -> new KycRecord(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class), rs.getString(3),
                        rs.getString(4), rs.getObject(5, UUID.class), instant(rs.getTimestamp(6)),
                        rs.getObject(7, UUID.class), instant(rs.getTimestamp(8)), rs.getString(9),
                        instant(rs.getTimestamp(10))), customerId).stream().findFirst().orElse(null);
    }

    private void requireCustomer(UUID customerId, boolean lock) {
        String sql = "select id from aml_customer where id=?" + (lock ? " for update" : "");
        if (jdbc.query(sql, (rs, row) -> rs.getObject(1, UUID.class), customerId).isEmpty()) {
            throw new NoSuchElementException("客户不存在");
        }
    }

    private UUID actorId() {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        return jdbc.query("select id from sys_user where username=?", (rs, row) -> rs.getObject(1, UUID.class),
                username).stream().findFirst().orElseThrow(() -> new IllegalStateException("操作用户不存在"));
    }

    private void event(UUID kycId, UUID customerId, String from, String to, UUID actor, String reason, Instant at) {
        jdbc.update("insert into aml_customer_kyc_history(id,kyc_id,customer_id,from_status,to_status,operator_id," 
                        + "reason,occurred_at) values(?,?,?,?,?,?,?,?)",
                UUID.randomUUID(), kycId, customerId, from, to, actor, reason, Timestamp.from(at));
    }

    private static Instant instant(Timestamp timestamp) { return timestamp == null ? null : timestamp.toInstant(); }
}
