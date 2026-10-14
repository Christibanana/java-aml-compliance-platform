package com.example.aml.account.dto;

import com.example.aml.account.AccountCustomer;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.util.UUID;

public record CreateAccountRequest(
        @NotBlank @Size(max = 64) @Pattern(regexp = "^[A-Za-z0-9_-]+$") String accountNo,
        @NotBlank @Pattern(regexp = "^[A-Za-z]{3}$") String currency,
        @NotNull UUID customerId,
        @NotNull AccountCustomer.RelationType relationType,
        @NotNull @DecimalMin("0.01") @DecimalMax("100.00") BigDecimal ownershipPercent) {}
