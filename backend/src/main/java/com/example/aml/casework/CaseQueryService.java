package com.example.aml.casework;

import com.example.aml.casework.dto.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;

@Service
public class CaseQueryService {
    private final JdbcTemplate jdbc;

    public CaseQueryService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public CasePage search(String status, String priority, UUID investigatorId, String keyword, int page, int size) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), 200);
        StringBuilder where = new StringBuilder(" where 1=1");
        List<Object> args = new ArrayList<>();
        addExact(where, args, "c.status", status);
        addExact(where, args, "c.priority", priority);
        if (investigatorId != null) {
            where.append(" and c.investigator_id=?");
            args.add(investigatorId);
        }
        if (keyword != null && !keyword.isBlank()) {
            where.append(" and (lower(c.case_no) like ? or lower(c.title) like ?)");
            String value = "%" + keyword.trim().toLowerCase() + "%";
            args.add(value);
            args.add(value);
        }
        String from = " from aml_case c left join sys_user u on u.id=c.investigator_id";
        Long total = jdbc.queryForObject("select count(*)" + from + where, Long.class, args.toArray());
        List<Object> pageArgs = new ArrayList<>(args);
        pageArgs.add(safeSize);
        pageArgs.add(safePage * safeSize);
        String sql = "select c.id,c.case_no,c.title,c.status,c.priority,c.investigator_id,"
                + "u.username investigator_username,c.created_at,c.updated_at,"
                + "(select count(*) from aml_case_alert ca where ca.case_id=c.id) alert_count,"
                + "(select count(*) from aml_case_note n where n.case_id=c.id) note_count,"
                + "(select count(*) from aml_case_evidence e where e.case_id=c.id and e.active=true) evidence_count"
                + from + where + " order by c.created_at desc limit ? offset ?";
        List<CaseSummary> content = jdbc.query(sql, (rs, rowNum) -> summary(rs), pageArgs.toArray());
        return new CasePage(content, total == null ? 0 : total, safePage, safeSize);
    }

    public CaseDetail get(UUID id) {
        String sql = "select c.id,c.case_no,c.title,c.status,c.priority,c.investigator_id,"
                + "u.username investigator_username,c.conclusion,c.submitted_at,c.closed_at,c.created_at,c.updated_at,"
                + "(select count(*) from aml_case_alert ca where ca.case_id=c.id) alert_count,"
                + "(select count(*) from aml_case_note n where n.case_id=c.id) note_count,"
                + "(select count(*) from aml_case_evidence e where e.case_id=c.id and e.active=true) evidence_count "
                + "from aml_case c left join sys_user u on u.id=c.investigator_id where c.id=?";
        List<CaseDetail> rows = jdbc.query(sql, (rs, rowNum) -> new CaseDetail(summary(rs),
                rs.getString("conclusion"), instant(rs.getTimestamp("submitted_at")),
                instant(rs.getTimestamp("closed_at")), alerts(id), notes(id), evidence(id), history(id)), id);
        if (rows.isEmpty()) throw new NoSuchElementException("案件不存在");
        return rows.getFirst();
    }

    public List<InvestigatorSummary> investigators() {
        return jdbc.query("select distinct u.id,u.username,u.display_name from sys_user u "
                        + "join sys_user_role ur on ur.user_id=u.id "
                        + "join sys_role_permission rp on rp.role_id=ur.role_id "
                        + "join sys_permission p on p.id=rp.permission_id "
                        + "where u.enabled=true and p.code='CASE_INVESTIGATE' order by u.username",
                (rs, rowNum) -> new InvestigatorSummary(rs.getObject("id", UUID.class),
                        rs.getString("username"), rs.getString("display_name")));
    }

    private List<CaseDetail.AlertItem> alerts(UUID caseId) {
        return jdbc.query("select a.id,a.alert_no,r.code rule_code,a.risk_level,a.status,a.summary "
                        + "from aml_case_alert ca join aml_alert a on a.id=ca.alert_id "
                        + "join aml_rule_hit h on h.id=a.rule_hit_id join aml_rule r on r.id=h.rule_id "
                        + "where ca.case_id=? order by a.created_at",
                (rs, rowNum) -> new CaseDetail.AlertItem(rs.getObject("id", UUID.class),
                        rs.getString("alert_no"), rs.getString("rule_code"), rs.getString("risk_level"),
                        rs.getString("status"), rs.getString("summary")), caseId);
    }

    private List<CaseDetail.NoteItem> notes(UUID caseId) {
        return jdbc.query("select n.id,n.author_id,u.username,n.content,n.created_at from aml_case_note n "
                        + "left join sys_user u on u.id=n.author_id where n.case_id=? order by n.created_at",
                (rs, rowNum) -> new CaseDetail.NoteItem(rs.getObject("id", UUID.class),
                        rs.getObject("author_id", UUID.class), rs.getString("username"), rs.getString("content"),
                        instant(rs.getTimestamp("created_at"))), caseId);
    }

    private List<CaseDetail.EvidenceItem> evidence(UUID caseId) {
        return jdbc.query("select e.id,e.original_name,e.content_type,e.size_bytes,e.sha256,e.uploaded_by,"
                        + "u.username,e.created_at from aml_case_evidence e left join sys_user u on u.id=e.uploaded_by "
                        + "where e.case_id=? and e.active=true order by e.created_at",
                (rs, rowNum) -> new CaseDetail.EvidenceItem(rs.getObject("id", UUID.class),
                        rs.getString("original_name"), rs.getString("content_type"), rs.getLong("size_bytes"),
                        rs.getString("sha256").trim(), rs.getObject("uploaded_by", UUID.class),
                        rs.getString("username"), instant(rs.getTimestamp("created_at"))), caseId);
    }

    private List<CaseDetail.HistoryItem> history(UUID caseId) {
        return jdbc.query("select h.id,h.from_status,h.to_status,h.operator_id,u.username,h.comment,h.occurred_at "
                        + "from aml_case_history h left join sys_user u on u.id=h.operator_id "
                        + "where h.case_id=? order by h.occurred_at",
                (rs, rowNum) -> new CaseDetail.HistoryItem(rs.getObject("id", UUID.class),
                        rs.getString("from_status"), rs.getString("to_status"),
                        rs.getObject("operator_id", UUID.class), rs.getString("username"), rs.getString("comment"),
                        instant(rs.getTimestamp("occurred_at"))), caseId);
    }

    private CaseSummary summary(ResultSet rs) throws SQLException {
        return new CaseSummary(rs.getObject("id", UUID.class), rs.getString("case_no"), rs.getString("title"),
                rs.getString("status"), rs.getString("priority"), rs.getObject("investigator_id", UUID.class),
                rs.getString("investigator_username"), rs.getLong("alert_count"), rs.getLong("note_count"),
                rs.getLong("evidence_count"), instant(rs.getTimestamp("created_at")),
                instant(rs.getTimestamp("updated_at")));
    }

    private void addExact(StringBuilder where, List<Object> args, String column, String value) {
        if (value != null && !value.isBlank()) {
            where.append(" and ").append(column).append("=?");
            args.add(value.trim().toUpperCase());
        }
    }

    private static Instant instant(Timestamp value) {
        return value == null ? null : value.toInstant();
    }
}
