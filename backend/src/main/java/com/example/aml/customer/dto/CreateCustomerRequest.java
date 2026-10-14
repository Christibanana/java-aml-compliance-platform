package com.example.aml.customer.dto;

import com.example.aml.customer.Customer;
import jakarta.validation.constraints.*;

public record CreateCustomerRequest(
        @NotBlank @Size(max = 40) @Pattern(regexp = "^[A-Za-z0-9_-]+$") String customerNo,
        @NotNull Customer.CustomerType customerType,
        @NotBlank @Size(max = 200) String fullName,
        @Size(max = 30) String idType,
        @Size(max = 100) String idNumberMasked,
        @Pattern(regexp = "^[A-Za-z]{2}$") String countryCode,
        @NotNull Customer.RiskLevel riskLevel) {}
