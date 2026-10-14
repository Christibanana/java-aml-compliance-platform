package com.example.aml.account;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

@Embeddable
public class AccountCustomerId implements Serializable {
    @Column(name = "account_id") private UUID accountId;
    @Column(name = "customer_id") private UUID customerId;

    protected AccountCustomerId() {}
    public AccountCustomerId(UUID accountId, UUID customerId) {
        this.accountId = accountId;
        this.customerId = customerId;
    }
    public UUID getAccountId() { return accountId; }
    public UUID getCustomerId() { return customerId; }
    @Override public boolean equals(Object object) {
        if (this == object) return true;
        if (!(object instanceof AccountCustomerId other)) return false;
        return Objects.equals(accountId, other.accountId) && Objects.equals(customerId, other.customerId);
    }
    @Override public int hashCode() { return Objects.hash(accountId, customerId); }
}
