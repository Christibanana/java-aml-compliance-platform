package com.example.aml.integration.fineract;

import com.example.aml.account.*;
import com.example.aml.audit.AuditService;
import com.example.aml.transaction.*;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class FineractImportServiceTest {
    @Test void importsMappedDepositAndRejectsReversedTransaction() {
        FineractClient client = mock(FineractClient.class);
        TransactionRepository transactions = mock(TransactionRepository.class);
        TransactionImportBatchRepository batches = mock(TransactionImportBatchRepository.class);
        AccountRepository accounts = mock(AccountRepository.class);
        AuditService audit = mock(AuditService.class);
        Account account = new Account("ACC-7", "USD");
        when(accounts.findByAccountNo("ACC-7")).thenReturn(Optional.of(account));
        when(batches.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(client.fetchSavingsAccount(7)).thenReturn(new FineractClient.SavingsAccount(7, "ACC-7", "USD", List.of(
                new FineractClient.Transaction(31, LocalDate.of(2026, 10, 14), new BigDecimal("125.50"), true, false, false, "deposit"),
                new FineractClient.Transaction(32, LocalDate.of(2026, 10, 14), new BigDecimal("10.00"), false, true, true, "withdrawal"))));
        var result = new FineractImportService(client, transactions, batches, accounts, audit).importSavingsAccount(7);
        assertThat(result.imported()).isEqualTo(1);
        assertThat(result.rejected()).isEqualTo(1);
        assertThat(result.errors()).singleElement().asString().contains("已冲正");
        verify(transactions).save(argThat(transaction -> transaction.getSource().equals("FINERACT")
                && transaction.getDirection().equals("IN")
                && transaction.getExternalId().equals("SAV-7-TX-31")));
    }
}
