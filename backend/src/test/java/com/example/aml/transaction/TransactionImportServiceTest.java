package com.example.aml.transaction;

import com.example.aml.account.*;
import com.example.aml.audit.AuditService;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import java.nio.charset.StandardCharsets;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class TransactionImportServiceTest {
    private final TransactionRepository transactions = mock(TransactionRepository.class);
    private final TransactionImportBatchRepository batches = mock(TransactionImportBatchRepository.class);
    private final AccountRepository accounts = mock(AccountRepository.class);
    private final AuditService audit = mock(AuditService.class);
    private final TransactionImportService service = new TransactionImportService(transactions, batches, accounts, audit);

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
}
