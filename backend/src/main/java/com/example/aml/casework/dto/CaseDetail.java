package com.example.aml.casework.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record CaseDetail(CaseSummary caseInfo, String conclusion, Instant submittedAt, Instant closedAt,
        List<AlertItem> alerts, List<NoteItem> notes, List<EvidenceItem> evidence, List<HistoryItem> history) {
    public record AlertItem(UUID id, String alertNo, String ruleCode, String riskLevel, String status, String summary) {}
    public record NoteItem(UUID id, UUID authorId, String authorUsername, String content, Instant createdAt) {}
    public record EvidenceItem(UUID id, String originalName, String contentType, long sizeBytes, String sha256,
            UUID uploadedBy, String uploaderUsername, Instant createdAt) {}
    public record HistoryItem(UUID id, String fromStatus, String toStatus, UUID operatorId,
            String operatorUsername, String comment, Instant occurredAt) {}
}
