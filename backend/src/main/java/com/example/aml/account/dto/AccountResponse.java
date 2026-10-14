package com.example.aml.account.dto;

import com.example.aml.account.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record AccountResponse(UUID id, String accountNo, String currency, Account.Status status,
                              Instant openedAt, Instant closedAt, List<CustomerRelation> customers,
                              Instant createdAt, Instant updatedAt) {
    public record CustomerRelation(UUID customerId, String customerNo, String fullName,
                                   AccountCustomer.RelationType relationType, BigDecimal ownershipPercent) {}
    public static AccountResponse from(Account account, List<AccountCustomer> links) {
        List<CustomerRelation> relations = links.stream().map(link -> new CustomerRelation(
                link.getCustomer().getId(), link.getCustomer().getCustomerNo(), link.getCustomer().getFullName(),
                link.getRelationType(), link.getOwnershipPercent())).toList();
        return new AccountResponse(account.getId(), account.getAccountNo(), account.getCurrency(), account.getStatus(),
                account.getOpenedAt(), account.getClosedAt(), relations, account.getCreatedAt(), account.getUpdatedAt());
    }
}
