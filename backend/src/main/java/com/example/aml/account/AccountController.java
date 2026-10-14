package com.example.aml.account;

import com.example.aml.account.dto.*;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

@RestController
@RequestMapping("/api/accounts")
public class AccountController {
    private final AccountService accountService;
    public AccountController(AccountService accountService) { this.accountService = accountService; }

    @GetMapping @PreAuthorize("hasAuthority('CUSTOMER_READ')")
    public Page<AccountResponse> list(@RequestParam(required = false) UUID customerId,
                                      @RequestParam(defaultValue = "0") int page,
                                      @RequestParam(defaultValue = "20") int size) {
        return accountService.list(customerId, page, size);
    }

    @GetMapping("/{id}") @PreAuthorize("hasAuthority('CUSTOMER_READ')")
    public AccountResponse get(@PathVariable UUID id) { return accountService.get(id); }

    @PostMapping @PreAuthorize("hasAuthority('ACCOUNT_WRITE')")
    public AccountResponse create(@Valid @RequestBody CreateAccountRequest request) {
        return accountService.create(request);
    }

    @PutMapping("/{id}/status") @PreAuthorize("hasAuthority('ACCOUNT_WRITE')")
    public AccountResponse changeStatus(@PathVariable UUID id,
                                        @Valid @RequestBody UpdateAccountStatusRequest request) {
        return accountService.changeStatus(id, request);
    }
}
