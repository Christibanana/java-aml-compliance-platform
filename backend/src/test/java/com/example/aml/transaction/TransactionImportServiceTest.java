package com.example.aml.transaction;

import com.example.aml.account.*;
import com.example.aml.audit.AuditService;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import java.nio.charset.StandardCharsets;
import java.io.InputStream;
import java.util.List;
import java.util.Optional;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class TransactionImportServiceTest {
    private final TransactionRepository transactions = mock(TransactionRepository.class);
    private final TransactionImportBatchRepository batches = mock(TransactionImportBatchRepository.class);
    private final AccountRepository accounts = mock(AccountRepository.class);
    private final AuditService audit = mock(AuditService.class);
    private final ImportChunkWriter writer = mock(ImportChunkWriter.class);
    private final TransactionImportService service = new TransactionImportService(transactions, batches, accounts, audit, writer);

    @Test void rejectsInvalidHeader() {
        MockMultipartFile file = new MockMultipartFile("file", "bad.csv", "text/csv",
                "wrong,header\n1,2".getBytes(StandardCharsets.UTF_8));
        when(batches.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        assertThatThrownBy(() -> service.importCsv(file)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("表头");
    }

    @Test void parsesQuotedCommaAndEscapedQuote() {
        assertThat(TransactionImportService.parseCsvLine("A,\"B,C\",\"D\"\"E\""))
                .isEqualTo(List.of("A", "B,C", "D\"E"));
    }

    @Test void streamsMultilineBomAndDuplicateFromCsvFixture() throws Exception {
        try (InputStream input = getClass().getResourceAsStream("/csv/quoted-multiline.csv")) {
            assertThat(input).isNotNull();
            MockMultipartFile file = new MockMultipartFile("file", "transactions.csv", "text/csv", input);
            when(batches.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
            when(accounts.findByAccountNo("ACC-CSV-1")).thenReturn(Optional.of(new Account("ACC-CSV-1", "USD")));
            when(writer.save(any())).thenAnswer(invocation -> {
                List<ImportChunkWriter.Pending> chunk = invocation.getArgument(0);
                return new ImportChunkWriter.Outcome(chunk.size(), 0, 0, List.of());
            });
            var result = service.importCsv(file);
            assertThat(result.total()).isEqualTo(3);
            assertThat(result.imported()).isEqualTo(1);
            assertThat(result.duplicates()).isEqualTo(1);
            assertThat(result.rejected()).isEqualTo(1);
            verify(writer).save(argThat(chunk -> chunk.size() == 1 &&
                    chunk.getFirst().transaction().getDescription().equals("first line\nsecond, \"quoted\" line")));
        }
    }

    @Test void splitsLargeImportIntoBoundedChunks() throws Exception {
        StringBuilder csv = new StringBuilder(TransactionImportService.HEADER).append('\n');
        for (int number = 0; number < 1001; number++) {
            csv.append("TX-LARGE-").append(number)
                    .append(",ACC-CSV-1,,IN,10.00,USD,2026-10-14T10:00:00Z,TRANSFER,synthetic\n");
        }
        when(batches.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(accounts.findByAccountNo("ACC-CSV-1")).thenReturn(Optional.of(new Account("ACC-CSV-1", "USD")));
        when(writer.save(any())).thenAnswer(invocation -> {
            List<ImportChunkWriter.Pending> chunk = invocation.getArgument(0);
            return new ImportChunkWriter.Outcome(chunk.size(), 0, 0, List.of());
        });
        MockMultipartFile file = new MockMultipartFile("file", "large.csv", "text/csv",
                csv.toString().getBytes(StandardCharsets.UTF_8));
        var result = service.importCsv(file);
        assertThat(result.imported()).isEqualTo(1001);
        verify(writer, times(10)).save(argThat(chunk -> chunk.size() == 100));
        verify(writer).save(argThat(chunk -> chunk.size() == 1));
    }
}
