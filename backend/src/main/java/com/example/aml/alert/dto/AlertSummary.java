package com.example.aml.alert.dto;

import java.time.Instant;
import java.util.UUID;

public record AlertSummary(UUID id, String alertNo, UUID customerId, String customerNo, String customerName,
        String ruleCode, int ruleVersion, String riskLevel, String status, UUID assignedTo,
        String assignedUsername, String summary, long transactionCount, Instant createdAt) {
}
