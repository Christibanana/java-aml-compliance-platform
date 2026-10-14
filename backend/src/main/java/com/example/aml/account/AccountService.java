package com.example.aml.account;

import com.example.aml.account.dto.*;
import com.example.aml.audit.AuditService;
import com.example.aml.customer.Customer;
import com.example.aml.customer.CustomerRepository;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class AccountService {
    private final AccountRepository accounts;
    private final AccountCustomerRepository accountCustomers;
    private final CustomerRepository customers;
    private final AuditService audit;

    public AccountService(AccountRepository accounts, AccountCustomerRepository accountCustomers,
                          CustomerRepository customers, AuditService audit) {
        this.accounts = accounts;
        this.accountCustomers = accountCustomers;
        this.customers = customers;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public Page<AccountResponse> list(UUID customerId, int page, int size) {
        PageRequest pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100),
                Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<Account> result = customerId == null ? accounts.findAll(pageable)
                : accounts.findByCustomerId(customerId, pageable);
        Map<UUID, List<AccountCustomer>> relations = accountCustomers
                .findByAccount_IdIn(result.stream().map(Account::getId).toList()).stream()
                .collect(Collectors.groupingBy(link -> link.getAccount().getId()));
        return result.map(account -> AccountResponse.from(account,
                relations.getOrDefault(account.getId(), List.of())));
    }

    @Transactional(readOnly = true)
    public AccountResponse get(UUID id) {
        Account account = find(id);
        return AccountResponse.from(account, accountCustomers.findByAccount_Id(id));
    }

    @Transactional
    public AccountResponse create(CreateAccountRequest request) {
        String accountNo = request.accountNo().trim();
        if (accounts.existsByAccountNo(accountNo)) throw new IllegalArgumentException("账号已存在");
        Customer customer = customers.findById(request.customerId())
                .orElseThrow(() -> new NoSuchElementException("客户不存在"));
        if (customer.getStatus() != Customer.Status.ACTIVE) throw new IllegalArgumentException("停用客户不能开立账户");
        Account account = accounts.save(new Account(accountNo, request.currency().trim().toUpperCase()));
        AccountCustomer link = accountCustomers.save(new AccountCustomer(account, customer,
                request.relationType(), request.ownershipPercent()));
        audit.record("CREATE", "ACCOUNT", account.getId(), "SUCCESS",
                Map.of("customerId", customer.getId(), "accountNo", accountNo));
        return AccountResponse.from(account, List.of(link));
    }

    @Transactional
    public AccountResponse changeStatus(UUID id, UpdateAccountStatusRequest request) {
        Account account = find(id);
        account.changeStatus(request.status());
        audit.record("UPDATE_STATUS", "ACCOUNT", id, "SUCCESS", Map.of("status", request.status().name()));
        return AccountResponse.from(account, accountCustomers.findByAccount_Id(id));
    }

    private Account find(UUID id) {
        return accounts.findById(id).orElseThrow(() -> new NoSuchElementException("账户不存在"));
    }
}
