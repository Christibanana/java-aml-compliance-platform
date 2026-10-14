package com.example.aml.alert;
import com.example.aml.audit.AuditService; import org.junit.jupiter.api.Test; import org.springframework.jdbc.core.JdbcTemplate; import java.util.*; import static org.assertj.core.api.Assertions.*; import static org.mockito.Mockito.*;
class AlertServiceTest {
    @Test void shouldRejectIllegalTransition(){JdbcTemplate jdbc=mock(JdbcTemplate.class);AuditService audit=mock(AuditService.class);UUID id=UUID.randomUUID();when(jdbc.query(eq("select status from aml_alert where id=?"),any(org.springframework.jdbc.core.RowMapper.class),eq(id))).thenReturn(List.of("NEW"));AlertService service=new AlertService(jdbc,audit);assertThatThrownBy(()->service.transition(id,AlertService.Status.ESCALATED,"skip",UUID.randomUUID())).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("非法预警状态转换");verify(jdbc,never()).update(startsWith("update aml_alert"),any(Object[].class));}
}
