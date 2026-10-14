package com.example.aml.screening;

import com.example.aml.alert.AlertService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/screenings")
@PreAuthorize("hasAuthority('SCREENING_EXECUTE')")
public class ScreeningController {
    private final ScreeningService service;
    private final AlertService users;

    public ScreeningController(ScreeningService service, AlertService users) {
        this.service = service;
        this.users = users;
    }

    public record Request(UUID customerId, @NotBlank @Size(max = 200) String name,
                          @Pattern(regexp = "^$|^[A-Z]{2}$") String countryCode,
                          @Pattern(regexp = "^(MOCK|YENTE)?$") String provider,
                          @Pattern(regexp = "^(Person|Organization|LegalEntity)$") String schema) {
        public Request {
            if (schema == null || schema.isBlank()) schema = "LegalEntity";
        }
    }
    public record ReviewRequest(@NotNull ScreeningService.ReviewDecision decision,
                                @NotBlank @Size(max = 2000) String comment) {}

    @PostMapping
    public ScreeningService.ScreeningDetail screen(@Valid @RequestBody Request request) {
        return service.screen(request.customerId(), request.name(), request.countryCode(), request.schema(),
                request.provider(), users.currentUserId());
    }

    @GetMapping
    public List<ScreeningService.ScreeningSummary> list() { return service.list(); }

    @GetMapping("/{id}")
    public ScreeningService.ScreeningDetail get(@PathVariable UUID id) { return service.get(id); }

    @PostMapping("/{requestId}/matches/{matchId}/review")
    public ScreeningService.ScreeningDetail review(@PathVariable UUID requestId, @PathVariable UUID matchId,
            @Valid @RequestBody ReviewRequest request) {
        return service.review(requestId, matchId, request.decision(), request.comment(), users.currentUserId());
    }

    @GetMapping("/providers")
    public List<ScreeningProviderRegistry.ProviderInfo> providers() { return service.providerInfo(); }
}
