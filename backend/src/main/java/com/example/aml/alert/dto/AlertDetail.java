package com.example.aml.alert.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record AlertDetail(AlertSummary alert, String hitKey, String explanation, String closedReason,
        List<TransactionItem> transactions, List<HistoryItem> history) {
    public record TransactionItem(UUID id, String transactionNo, BigDecimal amount, String currency,
            String direction, Instant transactionTime) {
    }

    public record HistoryItem(UUID id, String fromStatus, String toStatus, UUID operatorId,
            String operatorUsername, String comment, Instant createdAt) {
    }
}
