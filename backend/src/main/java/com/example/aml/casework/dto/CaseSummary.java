package com.example.aml.casework.dto;

import java.time.Instant;
import java.util.UUID;

public record CaseSummary(UUID id, String caseNo, String title, String status, String priority,
        UUID investigatorId, String investigatorUsername, long alertCount, long noteCount,
        long evidenceCount, Instant createdAt, Instant updatedAt) {
}
