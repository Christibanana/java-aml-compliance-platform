package com.example.aml.alert;

import com.example.aml.audit.AuditService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.sql.Timestamp;
import java.util.*;

@Service
public class AlertService {
    public enum Status {NEW, ASSIGNED, IN_REVIEW, CLOSED, ESCALATED}

    private static final Map<Status, Set<Status>> ALLOWED = Map.of(
            Status.NEW, EnumSet.of(Status.ASSIGNED, Status.IN_REVIEW, Status.CLOSED),
            Status.ASSIGNED, EnumSet.of(Status.IN_REVIEW, Status.CLOSED),
            Status.IN_REVIEW, EnumSet.of(Status.CLOSED, Status.ESCALATED),
            Status.CLOSED, EnumSet.noneOf(Status.class),
            Status.ESCALATED, EnumSet.noneOf(Status.class));

    private final JdbcTemplate jdbc;
    private final AuditService audit;

    public AlertService(JdbcTemplate jdbc, AuditService audit) {
        this.jdbc = jdbc;
        this.audit = audit;
    }

    @Transactional
    public void assign(UUID alertId, UUID assigneeId) {
        Status current = Status.valueOf(requireAlert(alertId));
        Integer count = jdbc.queryForObject("select count(*) from sys_user where id=? and enabled=true",
                Integer.class, assigneeId);
        if (count == null || count == 0) throw new NoSuchElementException("分派用户不存在或已禁用");
        if (current != Status.NEW && current != Status.ASSIGNED) {
            throw new IllegalArgumentException("只有新建或已分派预警可以重新分派");
        }
        UUID operator = currentUserId();
        jdbc.update("update aml_alert set status='ASSIGNED',assigned_to=?,updated_at=? where id=?",
                assigneeId, Timestamp.from(Instant.now()), alertId);
        jdbc.update("insert into aml_alert_history(id,alert_id,from_status,to_status,operator_id,comment) "
                        + "values(?,?,?,?,?,?)", UUID.randomUUID(), alertId, current.name(), Status.ASSIGNED.name(),
                operator, "分派预警");
        audit.record("ASSIGN", "ALERT", alertId, "SUCCESS", Map.of("assigneeId", assigneeId));
    }

    @Transactional
    public void transition(UUID id, Status target, String comment, UUID operator) {
        Status current = Status.valueOf(requireAlert(id));
        if (!ALLOWED.get(current).contains(target)) {
            throw new IllegalArgumentException("非法预警状态转换：" + current + " -> " + target);
        }
        jdbc.update("update aml_alert set status=?,closed_reason=case when ?='CLOSED' then ? else closed_reason end,"
                        + "updated_at=? where id=?", target.name(), target.name(), comment,
                Timestamp.from(Instant.now()), id);
        jdbc.update("insert into aml_alert_history(id,alert_id,from_status,to_status,operator_id,comment) "
                        + "values(?,?,?,?,?,?)", UUID.randomUUID(), id, current.name(), target.name(), operator, comment);
        audit.record("TRANSITION", "ALERT", id, "SUCCESS", Map.of("from", current, "to", target));
    }

    @Transactional
    public UUID escalate(UUID alertId, String title) {
        Map<String, Object> alert = jdbc.queryForMap("select status,risk_level from aml_alert where id=?", alertId);
        Status current = Status.valueOf((String) alert.get("status"));
        if (title == null || title.isBlank()) throw new IllegalArgumentException("Case title must not be blank");
        if (current != Status.IN_REVIEW) throw new IllegalArgumentException("只有审核中的预警可以升级案件");
        UUID caseId = UUID.randomUUID();
        UUID operator = currentUserId();
        String caseNo = "CASE-" + Instant.now().toEpochMilli() + "-"
                + caseId.toString().substring(0, 8).toUpperCase(Locale.ROOT);
        jdbc.update("insert into aml_case(id,case_no,title,status,priority) values(?,?,?,?,?)",
                caseId, caseNo, title.trim(), "OPEN", alert.get("risk_level"));
        jdbc.update("insert into aml_case_alert(case_id,alert_id) values(?,?)", caseId, alertId);
        transition(alertId, Status.ESCALATED, "升级为案件 " + caseNo, operator);
        jdbc.update("insert into aml_case_history(id,case_id,to_status,operator_id,comment) values(?,?,?,?,?)",
                UUID.randomUUID(), caseId, "OPEN", operator, "由预警升级创建");
        audit.record("CREATE", "CASE", caseId, "SUCCESS", Map.of("alertId", alertId));
        return caseId;
    }

    public UUID currentUserId() {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        return jdbc.queryForObject("select id from sys_user where username=?", UUID.class, username);
    }

    private String requireAlert(UUID id) {
        var rows = jdbc.query("select status from aml_alert where id=?", (rs, rowNum) -> rs.getString(1), id);
        if (rows.isEmpty()) throw new NoSuchElementException("预警不存在");
        return rows.getFirst();
    }
}
