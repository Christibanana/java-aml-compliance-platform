package com.example.aml.account;

import com.example.aml.customer.Customer;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "aml_account_customer")
public class AccountCustomer {
    public enum RelationType { OWNER, JOINT_OWNER, AUTHORIZED_SIGNER }

    @EmbeddedId private AccountCustomerId id;
    @MapsId("accountId") @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_id") private Account account;
    @MapsId("customerId") @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id") private Customer customer;
    @Enumerated(EnumType.STRING) @Column(name = "relation_type", nullable = false, length = 20)
    private RelationType relationType;
    @Column(name = "ownership_percent", precision = 5, scale = 2)
    private BigDecimal ownershipPercent;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected AccountCustomer() {}
    public AccountCustomer(Account account, Customer customer, RelationType relationType, BigDecimal ownershipPercent) {
        this.id = new AccountCustomerId(account.getId(), customer.getId());
        this.account = account;
        this.customer = customer;
        this.relationType = relationType;
        this.ownershipPercent = ownershipPercent;
    }
    @PrePersist void createTimestamp() { if (createdAt == null) createdAt = Instant.now(); }
    public Account getAccount() { return account; }
    public Customer getCustomer() { return customer; }
    public RelationType getRelationType() { return relationType; }
    public BigDecimal getOwnershipPercent() { return ownershipPercent; }
}
