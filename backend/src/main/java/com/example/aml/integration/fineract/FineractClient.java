package com.example.aml.integration.fineract;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public interface FineractClient {
    boolean available();
    SavingsAccount fetchSavingsAccount(long savingsAccountId);

    record SavingsAccount(long id, String accountNo, String currency, List<Transaction> transactions) {}
    record Transaction(long id, LocalDate date, BigDecimal amount, boolean deposit,
                       boolean withdrawal, boolean reversed, String typeCode) {}
}
