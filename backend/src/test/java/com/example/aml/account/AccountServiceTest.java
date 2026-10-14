package com.example.aml.account;

import com.example.aml.account.dto.CreateAccountRequest;
import com.example.aml.audit.AuditService;
import com.example.aml.customer.*;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class AccountServiceTest {
    private final AccountRepository accounts = mock(AccountRepository.class);
    private final AccountCustomerRepository links = mock(AccountCustomerRepository.class);
    private final CustomerRepository customers = mock(CustomerRepository.class);
    private final AuditService audit = mock(AuditService.class);
    private final AccountService service = new AccountService(accounts, links, customers, audit);

    @Test void rejectsAccountForInactiveCustomer() {
        UUID customerId = UUID.randomUUID();
        Customer customer = new Customer("CUS-003", Customer.CustomerType.INDIVIDUAL, "Demo", null, null,
                "CN", Customer.RiskLevel.LOW);
        customer.update("Demo", null, null, "CN", Customer.RiskLevel.LOW, Customer.Status.INACTIVE);
        when(customers.findById(customerId)).thenReturn(Optional.of(customer));
        CreateAccountRequest request = new CreateAccountRequest("ACC-003", "CNY", customerId,
                AccountCustomer.RelationType.OWNER, new BigDecimal("100"));
        assertThatThrownBy(() -> service.create(request)).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("停用客户不能开立账户");
        verify(accounts, never()).save(any());
    }

    @Test void closedAccountCannotBeReactivated() {
        Account account = new Account("ACC-004", "CNY");
        account.changeStatus(Account.Status.CLOSED);
        assertThatThrownBy(() -> account.changeStatus(Account.Status.ACTIVE))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("已关闭账户不能重新启用");
    }
}
