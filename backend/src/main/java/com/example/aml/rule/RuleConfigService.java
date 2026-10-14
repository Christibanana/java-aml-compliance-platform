package com.example.aml.rule;

import com.example.aml.audit.AuditService;
import com.example.aml.rule.dto.*;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;

@Service
public class RuleConfigService {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final RuleParameterValidator validator;
    private final AuditService audit;

    public RuleConfigService(JdbcTemplate jdbc, ObjectMapper json, RuleParameterValidator validator, AuditService audit) {
        this.jdbc = jdbc;
        this.json = json;
        this.validator = validator;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public List<RuleSummary> list() {
        return jdbc.query("select r.id,r.code,r.name,r.rule_type,r.enabled,r.severity,r.active_version," +
                        "r.implementation_status,v.parameters_json,v.description from aml_rule r " +
                        "join aml_rule_version v on v.rule_id=r.id and v.version_no=r.active_version order by r.code",
                (rs, rowNum) -> summary(rs));
    }

    @Transactional(readOnly = true)
    public List<RuleVersionResponse> versions(String code) {
        requireRule(code, false);
        return jdbc.query("select v.id,v.version_no,v.parameters_json,v.description,v.created_at " +
                        "from aml_rule_version v join aml_rule r on r.id=v.rule_id where r.code=? order by v.version_no desc",
                (rs, rowNum) -> new RuleVersionResponse(rs.getObject("id", UUID.class), rs.getInt("version_no"),
                        readJson(rs.getString("parameters_json")), rs.getString("description"),
                        rs.getTimestamp("created_at").toInstant()), code);
    }

    @Transactional
    public RuleVersionResponse createVersion(String code, CreateRuleVersionRequest request) {
        Map<String, Object> rule = requireRule(code, true);
        if (!"IMPLEMENTED".equals(rule.get("implementation_status"))) {
            throw new IllegalArgumentException("该复杂规则标记为后续实现，当前不能启用新版本");
        }
        validator.validate(code, request.parameters());
        int nextVersion = ((Number) rule.get("active_version")).intValue() + 1;
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        jdbc.update("insert into aml_rule_version(id,rule_id,version_no,parameters_json,description,created_at,updated_at,version) " +
                        "values(?,?,?,?,?,?,?,0)", id, rule.get("id"), nextVersion,
                writeJson(request.parameters()), request.description().trim(), Timestamp.from(now), Timestamp.from(now));
        jdbc.update("update aml_rule set active_version=?,updated_at=?,version=version+1 where id=?",
                nextVersion, Timestamp.from(now), rule.get("id"));
        audit.record("CREATE_VERSION", "RULE", rule.get("id"), "SUCCESS",
                Map.of("code", code, "version", nextVersion));
        return new RuleVersionResponse(id, nextVersion, request.parameters(), request.description().trim(), now);
    }

    @Transactional
    public RuleSummary updateStatus(String code, UpdateRuleStatusRequest request) {
        Map<String, Object> rule = requireRule(code, true);
        if (request.enabled() && !"IMPLEMENTED".equals(rule.get("implementation_status"))) {
            throw new IllegalArgumentException("该规则算法尚未实现，不能启用");
        }
        jdbc.update("update aml_rule set enabled=?,updated_at=?,version=version+1 where id=?",
                request.enabled(), Timestamp.from(Instant.now()), rule.get("id"));
        audit.record("UPDATE_STATUS", "RULE", rule.get("id"), "SUCCESS",
                Map.of("code", code, "enabled", request.enabled()));
        return list().stream().filter(item -> item.code().equals(code)).findFirst().orElseThrow();
    }

    private Map<String, Object> requireRule(String code, boolean lock) {
        List<Map<String, Object>> rows = jdbc.queryForList("select id,code,active_version,implementation_status " +
                "from aml_rule where code=?" + (lock ? " for update" : ""), code);
        if (rows.isEmpty()) throw new NoSuchElementException("规则不存在");
        return rows.getFirst();
    }
    private RuleSummary summary(ResultSet rs) throws SQLException {
        return new RuleSummary(rs.getObject("id", UUID.class), rs.getString("code"), rs.getString("name"),
                rs.getString("rule_type"), rs.getBoolean("enabled"), rs.getString("severity"),
                rs.getInt("active_version"), rs.getString("implementation_status"),
                readJson(rs.getString("parameters_json")), rs.getString("description"));
    }
    private JsonNode readJson(String value) {
        try { return json.readTree(value); }
        catch (JsonProcessingException exception) { throw new IllegalStateException("规则参数格式错误", exception); }
    }
    private String writeJson(JsonNode value) {
        try { return json.writeValueAsString(value); }
        catch (JsonProcessingException exception) { throw new IllegalStateException("规则参数序列化失败", exception); }
    }
}
