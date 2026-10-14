package com.example.aml.casework;

import com.example.aml.audit.AuditService;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import java.sql.ResultSet;
import java.util.List;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class CaseServiceTest {
    @Test void closedCaseCannotReopen() throws Exception {
        JdbcTemplate jdbc = mock(JdbcTemplate.class); UUID id = UUID.randomUUID(); stubCase(jdbc, id, "CLOSED", null);
        CaseService service = new CaseService(jdbc, mock(AuditService.class));
        assertThatThrownBy(() -> service.transition(id, CaseService.Status.INVESTIGATING, "reopen", UUID.randomUUID()))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("非法案件状态转换");
    }

    @Test void caseMustBeAssignedBeforeInvestigation() throws Exception {
        JdbcTemplate jdbc = mock(JdbcTemplate.class); UUID id = UUID.randomUUID(); stubCase(jdbc, id, "OPEN", null);
        CaseService service = new CaseService(jdbc, mock(AuditService.class));
        assertThatThrownBy(() -> service.transition(id, CaseService.Status.INVESTIGATING, "start", UUID.randomUUID()))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("分派调查员");
    }

    @Test void pendingReviewRequiresInvestigationNote() throws Exception {
        JdbcTemplate jdbc = mock(JdbcTemplate.class); UUID id = UUID.randomUUID();
        stubCase(jdbc, id, "INVESTIGATING", UUID.randomUUID());
        when(jdbc.queryForObject(startsWith("select count(*) from aml_case_note"), eq(Long.class), eq(id))).thenReturn(0L);
        CaseService service = new CaseService(jdbc, mock(AuditService.class));
        assertThatThrownBy(() -> service.transition(id, CaseService.Status.PENDING_REVIEW, "review", UUID.randomUUID()))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("调查笔记");
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private void stubCase(JdbcTemplate jdbc, UUID id, String status, UUID investigatorId) throws Exception {
        ResultSet rs = mock(ResultSet.class); when(rs.getString("status")).thenReturn(status);
        when(rs.getObject("investigator_id", UUID.class)).thenReturn(investigatorId);
        when(jdbc.query(startsWith("select status,investigator_id from aml_case"), any(RowMapper.class), eq(id)))
                .thenAnswer(invocation -> List.of(((RowMapper) invocation.getArgument(1)).mapRow(rs, 0)));
    }
}
