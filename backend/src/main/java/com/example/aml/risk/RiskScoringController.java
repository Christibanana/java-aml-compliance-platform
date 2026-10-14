package com.example.aml.risk;

import com.example.aml.alert.AlertService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/risk-scores")
public class RiskScoringController {
    private final RiskScoringService service;
    private final AlertService users;

    public RiskScoringController(RiskScoringService service, AlertService users) {
        this.service = service;
        this.users = users;
    }

    @PostMapping("/customers/{id}/calculate")
    @PreAuthorize("hasAuthority('RULE_EXECUTE')")
    public RiskScoringService.ScoreResult calculateCustomer(@PathVariable UUID id) {
        return service.calculateCustomer(id, users.currentUserId());
    }
    @GetMapping("/customers/{id}")
    @PreAuthorize("hasAuthority('REPORT_READ')")
    public RiskScoringService.ScoreResult currentCustomer(@PathVariable UUID id) { return service.currentCustomer(id); }
    @GetMapping("/customers/{id}/history")
    @PreAuthorize("hasAuthority('REPORT_READ')")
    public List<RiskScoringService.ScoreResult> customerHistory(@PathVariable UUID id) {
        return service.customerHistory(id);
    }

    @PostMapping("/transactions/{id}/calculate")
    @PreAuthorize("hasAuthority('RULE_EXECUTE')")
    public RiskScoringService.ScoreResult calculateTransaction(@PathVariable UUID id) {
        return service.calculateTransaction(id, users.currentUserId());
    }
    @GetMapping("/transactions/{id}")
    @PreAuthorize("hasAuthority('REPORT_READ')")
    public RiskScoringService.ScoreResult currentTransaction(@PathVariable UUID id) {
        return service.currentTransaction(id);
    }
    @GetMapping("/transactions/{id}/history")
    @PreAuthorize("hasAuthority('REPORT_READ')")
    public List<RiskScoringService.ScoreResult> transactionHistory(@PathVariable UUID id) {
        return service.transactionHistory(id);
    }
}
