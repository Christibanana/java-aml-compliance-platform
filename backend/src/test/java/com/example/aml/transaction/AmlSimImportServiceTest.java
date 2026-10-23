package com.example.aml.transaction;

import com.example.aml.account.*;
import com.example.aml.audit.AuditService;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AmlSimImportServiceTest {
    private final TransactionRepository transactions = mock(TransactionRepository.class);
    private final TransactionImportBatchRepository batches = mock(TransactionImportBatchRepository.class);
    private final AccountRepository accounts = mock(AccountRepository.class);
    private final AuditService audit = mock(AuditService.class);
    private final ImportChunkWriter writer = mock(ImportChunkWriter.class);
    private final AmlSimImportService service = new AmlSimImportService(transactions, batches, accounts, audit, writer);

    @Test void importsOfficialTransactionSchemaAsSyntheticData() throws Exception {
        Account account = new Account("1001", "USD");
        when(batches.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(accounts.findByAccountNo("1001")).thenReturn(Optional.of(account));
        when(writer.save(any())).thenAnswer(invocation -> new ImportChunkWriter.Outcome(
                ((java.util.List<?>) invocation.getArgument(0)).size(), 0, 0, java.util.List.of()));
        MockMultipartFile file = new MockMultipartFile("file", "transactions.csv", "text/csv",
                (AmlSimImportService.HEADER + "\n1,2026-10-14T08:00:00Z,500.25,TRANSFER,1001,2002,true,9\n")
                        .getBytes(StandardCharsets.UTF_8));
        var result = service.importCsv(file, "USD");
        assertThat(result.imported()).isEqualTo(1);
        verify(writer).save(argThat(chunk -> chunk.size() == 1
                && chunk.getFirst().transaction().getSource().equals("AMLSIM")
                && chunk.getFirst().transaction().getDirection().equals("OUT")
                && chunk.getFirst().transaction().getExternalId().equals("AMLSIM-1")
                && chunk.getFirst().transaction().getDescription().contains("is_sar=true")));
    }

    @Test void rejectsFileWithoutOfficialColumns() {
        when(batches.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        MockMultipartFile file = new MockMultipartFile("file", "transactions.csv", "text/csv",
                "id,amount\n1,10\n".getBytes(StandardCharsets.UTF_8));
        assertThatThrownBy(() -> service.importCsv(file, "USD"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("官方交易列");
    }
}
