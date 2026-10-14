package com.example.aml.customer.dto;

import com.example.aml.customer.Customer;
import java.time.Instant;
import java.util.UUID;

public record CustomerResponse(UUID id, String customerNo, Customer.CustomerType customerType,
                               String fullName, String idType, String idNumberMasked, String countryCode,
                               Customer.RiskLevel riskLevel, Customer.Status status,
                               Instant createdAt, Instant updatedAt) {
    public static CustomerResponse from(Customer customer) {
        return new CustomerResponse(customer.getId(), customer.getCustomerNo(), customer.getCustomerType(),
                customer.getFullName(), customer.getIdType(), customer.getIdNumberMasked(),
                customer.getCountryCode(), customer.getRiskLevel(), customer.getStatus(),
                customer.getCreatedAt(), customer.getUpdatedAt());
    }
}
