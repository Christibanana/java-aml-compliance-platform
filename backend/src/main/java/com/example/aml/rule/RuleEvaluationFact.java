package com.example.aml.rule;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public class RuleEvaluationFact {
    private final String ruleCode;
    private final String subjectKey;
    private final UUID customerId;
    private final UUID accountId;
    private final List<UUID> transactionIds;
    private final BigDecimal metric;
    private final BigDecimal threshold;
    private final Instant windowStart;
    private final Instant windowEnd;
    private final String explanation;
    private boolean matched;

    public RuleEvaluationFact(String ruleCode, String subjectKey, UUID customerId, UUID accountId,
                              List<UUID> transactionIds, BigDecimal metric, BigDecimal threshold,
                              Instant windowStart, Instant windowEnd, String explanation) {
        this.ruleCode = ruleCode;
        this.subjectKey = subjectKey;
        this.customerId = customerId;
        this.accountId = accountId;
        this.transactionIds = List.copyOf(transactionIds);
        this.metric = metric;
        this.threshold = threshold;
        this.windowStart = windowStart;
        this.windowEnd = windowEnd;
        this.explanation = explanation;
    }

    public void markMatched() { matched = true; }
    public String getRuleCode() { return ruleCode; }
    public String getSubjectKey() { return subjectKey; }
    public UUID getCustomerId() { return customerId; }
    public UUID getAccountId() { return accountId; }
    public List<UUID> getTransactionIds() { return transactionIds; }
    public BigDecimal getMetric() { return metric; }
    public BigDecimal getThreshold() { return threshold; }
    public Instant getWindowStart() { return windowStart; }
    public Instant getWindowEnd() { return windowEnd; }
    public String getExplanation() { return explanation; }
    public boolean isMatched() { return matched; }
}
