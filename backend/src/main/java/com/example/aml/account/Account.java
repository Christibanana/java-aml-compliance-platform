package com.example.aml.account;

import com.example.aml.common.BaseEntity;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.Instant;

@Entity
@Table(name = "aml_account")
public class Account extends BaseEntity {
    public enum Status { ACTIVE, FROZEN, CLOSED }

    @Column(name = "account_no", nullable = false, unique = true, length = 64)
    private String accountNo;
    @JdbcTypeCode(SqlTypes.CHAR) @Column(nullable = false, columnDefinition = "char(3)")
    private String currency;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20)
    private Status status = Status.ACTIVE;
    @Column(name = "opened_at", nullable = false)
    private Instant openedAt;
    @Column(name = "closed_at")
    private Instant closedAt;

    protected Account() {}

    public Account(String accountNo, String currency) {
        this.accountNo = accountNo;
        this.currency = currency;
        this.openedAt = Instant.now();
    }

    public void changeStatus(Status newStatus) {
        if (status == Status.CLOSED && newStatus != Status.CLOSED) {
            throw new IllegalArgumentException("已关闭账户不能重新启用");
        }
        status = newStatus;
        closedAt = newStatus == Status.CLOSED ? Instant.now() : null;
    }

    public String getAccountNo() { return accountNo; }
    public String getCurrency() { return currency; }
    public Status getStatus() { return status; }
    public Instant getOpenedAt() { return openedAt; }
    public Instant getClosedAt() { return closedAt; }
}
