package com.example.aml.casework;

import com.example.aml.audit.AuditService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.mock.web.MockMultipartFile;
import java.nio.file.Path;
import java.sql.ResultSet;
import java.util.List;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class CaseEvidenceServiceTest {
    @TempDir Path tempDirectory;

    @Test void storesEvidenceWithDigestAndRejectsExecutableContent() throws Exception {
        JdbcTemplate jdbc = mock(JdbcTemplate.class); AuditService audit = mock(AuditService.class);
        UUID caseId = UUID.randomUUID(), operator = UUID.randomUUID(); stubOpenCase(jdbc, caseId);
        when(jdbc.update(startsWith("insert into aml_case_evidence"), any(Object[].class))).thenReturn(1);
        CaseEvidenceService service = new CaseEvidenceService(jdbc, audit, tempDirectory.toString(), 1024);
        service.initialize();
        UUID evidenceId = service.upload(caseId, operator,
                new MockMultipartFile("file", "statement.txt", "text/plain", "synthetic evidence".getBytes()));
        assertThat(tempDirectory.resolve(caseId.toString()).resolve(evidenceId + ".bin")).exists();
        verify(audit).record(eq("UPLOAD_EVIDENCE"), eq("CASE"), eq(caseId), eq("SUCCESS"), anyMap());
        assertThatThrownBy(() -> service.upload(caseId, operator,
                new MockMultipartFile("file", "payload.exe", "application/octet-stream", new byte[]{1})))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("不支持");
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private void stubOpenCase(JdbcTemplate jdbc, UUID caseId) throws Exception {
        ResultSet rs = mock(ResultSet.class); when(rs.getString(1)).thenReturn("OPEN");
        when(jdbc.query(startsWith("select status from aml_case"), any(RowMapper.class), eq(caseId)))
                .thenAnswer(invocation -> List.of(((RowMapper) invocation.getArgument(1)).mapRow(rs, 0)));
    }
}
