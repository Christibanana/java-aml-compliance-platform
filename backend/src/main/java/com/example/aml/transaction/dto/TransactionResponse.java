package com.example.aml.transaction.dto;

import com.example.aml.transaction.AmlTransaction;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record TransactionResponse(UUID id, String externalId, UUID accountId, String accountNo,
                                  String counterpartyAccount, String direction, BigDecimal amount,
                                  String currency, Instant transactionTime, String transactionType,
                                  String status, String source, String description, UUID importBatchId) {
    public static TransactionResponse from(AmlTransaction transaction) {
        return new TransactionResponse(transaction.getId(), transaction.getExternalId(),
                transaction.getAccount().getId(), transaction.getAccount().getAccountNo(),
                transaction.getCounterpartyAccount(), transaction.getDirection(), transaction.getAmount(),
                transaction.getCurrency(), transaction.getTransactionTime(), transaction.getTransactionType(),
                transaction.getStatus(), transaction.getSource(), transaction.getDescription(),
                transaction.getImportBatch() == null ? null : transaction.getImportBatch().getId());
    }
}
