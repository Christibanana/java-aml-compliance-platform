package com.example.aml.customer;

import com.example.aml.customer.dto.*;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

@RestController
@RequestMapping("/api/customers")
public class CustomerController {
    private final CustomerService customerService;

    public CustomerController(CustomerService customerService) { this.customerService = customerService; }

    @GetMapping @PreAuthorize("hasAuthority('CUSTOMER_READ')")
    public Page<CustomerResponse> list(@RequestParam(defaultValue = "") String keyword,
                                       @RequestParam(defaultValue = "0") int page,
                                       @RequestParam(defaultValue = "20") int size) {
        return customerService.list(keyword, page, size);
    }

    @GetMapping("/{id}") @PreAuthorize("hasAuthority('CUSTOMER_READ')")
    public CustomerResponse get(@PathVariable UUID id) { return customerService.get(id); }

    @PostMapping @PreAuthorize("hasAuthority('CUSTOMER_WRITE')")
    public CustomerResponse create(@Valid @RequestBody CreateCustomerRequest request) {
        return customerService.create(request);
    }

    @PutMapping("/{id}") @PreAuthorize("hasAuthority('CUSTOMER_WRITE')")
    public CustomerResponse update(@PathVariable UUID id, @Valid @RequestBody UpdateCustomerRequest request) {
        return customerService.update(id, request);
    }
}
