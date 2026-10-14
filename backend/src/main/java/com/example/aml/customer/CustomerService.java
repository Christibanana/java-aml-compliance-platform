package com.example.aml.customer;

import com.example.aml.audit.AuditService;
import com.example.aml.customer.dto.*;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;

@Service
public class CustomerService {
    private final CustomerRepository customers;
    private final AuditService audit;

    public CustomerService(CustomerRepository customers, AuditService audit) {
        this.customers = customers;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public Page<CustomerResponse> list(String keyword, int page, int size) {
        PageRequest pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100),
                Sort.by(Sort.Direction.DESC, "createdAt"));
        String normalized = keyword == null ? "" : keyword.trim();
        Page<Customer> result = normalized.isEmpty() ? customers.findAll(pageable)
                : customers.findByCustomerNoContainingIgnoreCaseOrFullNameContainingIgnoreCase(
                        normalized, normalized, pageable);
        return result.map(CustomerResponse::from);
    }

    @Transactional(readOnly = true)
    public CustomerResponse get(UUID id) { return CustomerResponse.from(find(id)); }

    @Transactional
    public CustomerResponse create(CreateCustomerRequest request) {
        String customerNo = request.customerNo().trim();
        if (customers.existsByCustomerNo(customerNo)) throw new IllegalArgumentException("客户编号已存在");
        Customer customer = customers.save(new Customer(customerNo, request.customerType(),
                request.fullName().trim(), trimToNull(request.idType()), trimToNull(request.idNumberMasked()),
                normalizeCountry(request.countryCode()), request.riskLevel()));
        audit.record("CREATE", "CUSTOMER", customer.getId(), "SUCCESS", Map.of("customerNo", customerNo));
        return CustomerResponse.from(customer);
    }

    @Transactional
    public CustomerResponse update(UUID id, UpdateCustomerRequest request) {
        Customer customer = find(id);
        customer.update(request.fullName().trim(), trimToNull(request.idType()),
                trimToNull(request.idNumberMasked()), normalizeCountry(request.countryCode()),
                request.riskLevel(), request.status());
        audit.record("UPDATE", "CUSTOMER", customer.getId(), "SUCCESS",
                Map.of("status", customer.getStatus().name()));
        return CustomerResponse.from(customer);
    }

    private Customer find(UUID id) {
        return customers.findById(id).orElseThrow(() -> new NoSuchElementException("客户不存在"));
    }

    private static String normalizeCountry(String value) {
        return value == null || value.isBlank() ? null : value.trim().toUpperCase();
    }

    private static String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
