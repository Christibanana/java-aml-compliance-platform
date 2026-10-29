package com.example.aml.customer;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.UUID;

@RestController
@RequestMapping("/api/customers/{customerId}/kyc")
public class KycController {
    public record SubmitRequest(@NotBlank @Size(max = 500) String documentSummary) {}
    public record ReviewRequest(@NotNull String decision, @NotBlank @Size(max = 500) String reason,
                                Instant expiresAt) {}

    private final KycService service;

    public KycController(KycService service) { this.service = service; }

    @GetMapping
    @PreAuthorize("hasAuthority('CUSTOMER_READ')")
    public KycService.KycDetail get(@PathVariable UUID customerId) { return service.get(customerId); }

    @PostMapping("/submit")
    @PreAuthorize("hasAuthority('CUSTOMER_WRITE')")
    public KycService.KycDetail submit(@PathVariable UUID customerId, @Valid @RequestBody SubmitRequest request) {
        return service.submit(customerId, request.documentSummary());
    }

    @PostMapping("/review")
    @PreAuthorize("hasAuthority('KYC_REVIEW')")
    public KycService.KycDetail review(@PathVariable UUID customerId, @Valid @RequestBody ReviewRequest request) {
        return service.review(customerId, request.decision(), request.reason(), request.expiresAt());
    }
}
