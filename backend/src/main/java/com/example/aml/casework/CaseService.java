package com.example.aml.casework;

import com.example.aml.audit.AuditService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;

@Service
public class CaseService {
    public enum Status {OPEN, INVESTIGATING, PENDING_REVIEW, CLOSED}

    private static final Map<Status, Set<Status>> ALLOWED = Map.of(
            Status.OPEN, EnumSet.of(Status.INVESTIGATING),
            Status.INVESTIGATING, EnumSet.of(Status.PENDING_REVIEW),
            Status.PENDING_REVIEW, EnumSet.of(Status.INVESTIGATING, Status.CLOSED),
            Status.CLOSED, EnumSet.noneOf(Status.class));

    private final JdbcTemplate jdbc;
    private final AuditService audit;

    public CaseService(JdbcTemplate jdbc, AuditService audit) {
        this.jdbc = jdbc;
        this.audit = audit;
    }

    @Transactional
    public void assign(UUID caseId, UUID investigatorId, UUID operatorId) {
        CaseState current = requireCase(caseId, true);
        if (current.status() == Status.CLOSED) throw new IllegalArgumentException("已关闭案件不能重新分派");
        Integer eligible = jdbc.queryForObject("select count(distinct u.id) from sys_user u "
                        + "join sys_user_role ur on ur.user_id=u.id join sys_role_permission rp on rp.role_id=ur.role_id "
                        + "join sys_permission p on p.id=rp.permission_id "
                        + "where u.id=? and u.enabled=true and p.code='CASE_INVESTIGATE'",
                Integer.class, investigatorId);
        if (eligible == null || eligible == 0) throw new IllegalArgumentException("调查员不存在、已禁用或没有案件调查权限");
        jdbc.update("update aml_case set investigator_id=?,updated_at=?,version=version+1 where id=?",
                investigatorId, Timestamp.from(Instant.now()), caseId);
        audit.record("ASSIGN", "CASE", caseId, "SUCCESS",
                Map.of("investigatorId", investigatorId, "operatorId", operatorId));
    }

    @Transactional
    public void addNote(UUID caseId, UUID authorId, String content) {
        CaseState current = requireCase(caseId, true);
        if (current.status() == Status.CLOSED) throw new IllegalArgumentException("已关闭案件不能添加调查笔记");
        UUID noteId = UUID.randomUUID();
        jdbc.update("insert into aml_case_note(id,case_id,author_id,content) values(?,?,?,?)",
                noteId, caseId, authorId, content.trim());
        audit.record("ADD_NOTE", "CASE", caseId, "SUCCESS", Map.of("noteId", noteId));
    }

    @Transactional
    public void transition(UUID id, Status target, String comment, UUID operatorId) {
        CaseState current = requireCase(id, true);
        if (!ALLOWED.get(current.status()).contains(target)) {
            throw new IllegalArgumentException("非法案件状态转换：" + current.status() + " -> " + target);
        }
        if (current.status() == Status.OPEN && target == Status.INVESTIGATING && current.investigatorId() == null) {
            throw new IllegalArgumentException("案件开始调查前必须分派调查员");
        }
        if (target == Status.PENDING_REVIEW) {
            Long noteCount = jdbc.queryForObject("select count(*) from aml_case_note where case_id=?", Long.class, id);
            if (noteCount == null || noteCount == 0) throw new IllegalArgumentException("提交复核前必须至少添加一条调查笔记");
        }
        Instant now = Instant.now();
        Timestamp timestamp = Timestamp.from(now);
        jdbc.update("update aml_case set status=?,submitted_at=case when ?='PENDING_REVIEW' then ? else submitted_at end,"
                        + "closed_at=case when ?='CLOSED' then ? else closed_at end,"
                        + "conclusion=case when ?='CLOSED' then ? else conclusion end,updated_at=?,version=version+1 where id=?",
                target.name(), target.name(), timestamp, target.name(), timestamp, target.name(), comment.trim(),
                timestamp, id);
        jdbc.update("insert into aml_case_history(id,case_id,from_status,to_status,operator_id,comment) "
                        + "values(?,?,?,?,?,?)", UUID.randomUUID(), id, current.status().name(), target.name(),
                operatorId, comment.trim());
        audit.record("TRANSITION", "CASE", id, "SUCCESS",
                Map.of("from", current.status(), "to", target));
    }

    private CaseState requireCase(UUID id, boolean lock) {
        List<CaseState> rows = jdbc.query("select status,investigator_id from aml_case where id=?"
                        + (lock ? " for update" : ""),
                (rs, rowNum) -> new CaseState(Status.valueOf(rs.getString("status")),
                        rs.getObject("investigator_id", UUID.class)), id);
        if (rows.isEmpty()) throw new NoSuchElementException("案件不存在");
        return rows.getFirst();
    }

    private record CaseState(Status status, UUID investigatorId) {}
}
