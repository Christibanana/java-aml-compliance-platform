package com.example.aml.customer;

import com.example.aml.audit.AuditService;
import com.example.aml.customer.dto.CreateCustomerRequest;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class CustomerServiceTest {
    private final CustomerRepository customers = mock(CustomerRepository.class);
    private final AuditService audit = mock(AuditService.class);
    private final CustomerService service = new CustomerService(customers, audit);

    @Test void rejectsDuplicateCustomerNumber() {
        when(customers.existsByCustomerNo("CUS-001")).thenReturn(true);
        CreateCustomerRequest request = new CreateCustomerRequest("CUS-001", Customer.CustomerType.INDIVIDUAL,
                "Synthetic Customer", null, null, "CN", Customer.RiskLevel.LOW);
        assertThatThrownBy(() -> service.create(request)).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("客户编号已存在");
        verify(customers, never()).save(any());
    }

    @Test void normalizesCountryAndTextWhenCreating() {
        when(customers.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        CreateCustomerRequest request = new CreateCustomerRequest("CUS-002", Customer.CustomerType.ORGANIZATION,
                "  Demo Company  ", " LICENSE ", " ****1234 ", "cn", Customer.RiskLevel.MEDIUM);
        var result = service.create(request);
        assertThat(result.fullName()).isEqualTo("Demo Company");
        assertThat(result.countryCode()).isEqualTo("CN");
        verify(audit).record(eq("CREATE"), eq("CUSTOMER"), any(), eq("SUCCESS"), anyMap());
    }
}
