package com.example.aml.alert;

import com.example.aml.alert.dto.AlertDetail;
import com.example.aml.alert.dto.AlertPage;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/alerts")
public class AlertController {
    private final AlertService service;
    private final AlertQueryService queries;

    public AlertController(AlertService service, AlertQueryService queries) {
        this.service = service;
        this.queries = queries;
    }

    public record AssignRequest(@NotNull UUID userId) {}
    public record TransitionRequest(@NotNull AlertService.Status status,
                                    @NotBlank @Size(max = 1000) String comment) {}
    public record EscalateRequest(@NotBlank @Size(max = 200) String title) {}

    @GetMapping
    @PreAuthorize("hasAuthority('ALERT_REVIEW')")
    public AlertPage list(@RequestParam(required = false) String status,
                          @RequestParam(required = false) String riskLevel,
                          @RequestParam(required = false) String ruleCode,
                          @RequestParam(required = false) String keyword,
                          @RequestParam(defaultValue = "0") int page,
                          @RequestParam(defaultValue = "20") int size) {
        return queries.search(status, riskLevel, ruleCode, keyword, page, size);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('ALERT_REVIEW')")
    public AlertDetail get(@PathVariable UUID id) {
        return queries.get(id);
    }

    @PostMapping("/{id}/assign")
    @PreAuthorize("hasAuthority('ALERT_REVIEW')")
    public AlertDetail assign(@PathVariable UUID id, @Valid @RequestBody AssignRequest request) {
        service.assign(id, request.userId());
        return queries.get(id);
    }

    @PostMapping("/{id}/transition")
    @PreAuthorize("hasAuthority('ALERT_REVIEW')")
    public AlertDetail transition(@PathVariable UUID id, @Valid @RequestBody TransitionRequest request) {
        service.transition(id, request.status(), request.comment(), service.currentUserId());
        return queries.get(id);
    }

    @PostMapping("/{id}/escalate")
    @PreAuthorize("hasAuthority('CASE_INVESTIGATE')")
    public Map<String, UUID> escalate(@PathVariable UUID id, @Valid @RequestBody EscalateRequest request) {
        return Map.of("caseId", service.escalate(id, request.title()));
    }
}
