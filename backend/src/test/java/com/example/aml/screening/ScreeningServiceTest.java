package com.example.aml.screening;

import com.example.aml.audit.AuditService;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ScreeningServiceTest {
    @Test void providerFailureIsPersistedAndAuditedInsteadOfBecomingEmptySuccess() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        ScreeningProviderRegistry registry = mock(ScreeningProviderRegistry.class);
        TransactionTemplate transactions = mock(TransactionTemplate.class);
        AuditService audit = mock(AuditService.class);
        ScreeningProvider provider = mock(ScreeningProvider.class);
        when(registry.require("YENTE")).thenReturn(provider);
        when(provider.name()).thenReturn("YENTE");
        when(provider.dataMode()).thenReturn(ScreeningProvider.DataMode.REAL);
        when(provider.search(any())).thenThrow(new ScreeningProviderException("Yente 返回 HTTP 503"));
        when(jdbc.update(anyString(), any(Object[].class))).thenReturn(1);
        ScreeningService service = new ScreeningService(jdbc, registry, transactions, audit);

        UUID operator = UUID.randomUUID();
        ScreeningProviderException failure = catchThrowableOfType(() -> service.screen(null, "External Name", "CN",
                "LegalEntity", "YENTE", operator), ScreeningProviderException.class);

        assertThat(failure.requestId()).isNotNull();
        assertThat(failure).hasMessageContaining("HTTP 503");
        verify(jdbc).update(startsWith("insert into aml_screening_request"), any(), isNull(), eq("External Name"),
                eq("YENTE"), eq("REAL"), eq(operator));
        verify(jdbc).update(startsWith("update aml_screening_request set status='FAILED'"),
                contains("HTTP 503"), any(), any(), eq(failure.requestId()));
        verify(audit).record(eq("SCREEN"), eq("SCREENING_REQUEST"), eq(failure.requestId()), eq("FAILED"), anyMap());
        verifyNoInteractions(transactions);
    }
}
