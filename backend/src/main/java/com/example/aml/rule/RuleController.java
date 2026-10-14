package com.example.aml.rule;

import com.example.aml.rule.dto.*;
import jakarta.validation.Valid;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@RequestMapping("/api/rules")
public class RuleController {
    private final RuleConfigService configService;
    private final RuleExecutionService executionService;
    private final DroolsRuleEvaluator drools;
    private final JdbcTemplate jdbc;

    public RuleController(RuleConfigService configService, RuleExecutionService executionService,
                          DroolsRuleEvaluator drools, JdbcTemplate jdbc) {
        this.configService = configService;
        this.executionService = executionService;
        this.drools = drools;
        this.jdbc = jdbc;
    }

    @GetMapping @PreAuthorize("hasAuthority('CUSTOMER_READ')")
    public List<RuleSummary> list() { return configService.list(); }
    @GetMapping("/{code}/versions") @PreAuthorize("hasAuthority('CUSTOMER_READ')")
    public List<RuleVersionResponse> versions(@PathVariable String code) { return configService.versions(code); }
    @PostMapping("/{code}/versions") @PreAuthorize("hasAuthority('RULE_EXECUTE')")
    public RuleVersionResponse createVersion(@PathVariable String code,
                                              @Valid @RequestBody CreateRuleVersionRequest request) {
        return configService.createVersion(code, request);
    }
    @PutMapping("/{code}/status") @PreAuthorize("hasAuthority('RULE_EXECUTE')")
    public RuleSummary updateStatus(@PathVariable String code,
                                    @Valid @RequestBody UpdateRuleStatusRequest request) {
        return configService.updateStatus(code, request);
    }
    @PostMapping("/execute") @PreAuthorize("hasAuthority('RULE_EXECUTE')")
    public RuleExecutionService.Result execute(@RequestBody(required = false) ExecuteRulesRequest request) {
        return executionService.execute(request);
    }
    @GetMapping("/compatibility") @PreAuthorize("hasAuthority('CUSTOMER_READ')")
    public Map<String, Object> compatibility() {
        return Map.of("droolsVersion", drools.runtimeVersion(), "javaVersion", System.getProperty("java.version"),
                "supportedRules", List.of("AML-R001", "AML-R002", "AML-R003"), "status", "COMPATIBLE");
    }
    @GetMapping("/executions") @PreAuthorize("hasAuthority('RULE_EXECUTE')")
    public List<Map<String, Object>> executions() {
        return jdbc.queryForList("select id,execution_key,scope,started_at,finished_at,status,processed_count," +
                "hit_count,window_start,window_end,error_message from aml_rule_execution order by started_at desc limit 100");
    }
    @GetMapping("/hits") @PreAuthorize("hasAuthority('CUSTOMER_READ')")
    public List<Map<String, Object>> hits(@RequestParam(required = false) String ruleCode) {
        String base = "select h.id,h.hit_key,h.rule_version,h.customer_id,h.score,h.explanation," +
                "h.created_at,r.code rule_code from aml_rule_hit h join aml_rule r on r.id=h.rule_id ";
        return ruleCode == null || ruleCode.isBlank()
                ? jdbc.queryForList(base + "order by h.created_at desc limit 200")
                : jdbc.queryForList(base + "where r.code=? order by h.created_at desc limit 200", ruleCode);
    }
}
