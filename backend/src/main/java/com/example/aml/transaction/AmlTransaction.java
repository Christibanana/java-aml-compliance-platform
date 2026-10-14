package com.example.aml.transaction;

import com.example.aml.account.Account;
import com.example.aml.common.BaseEntity;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "aml_transaction", uniqueConstraints = @UniqueConstraint(
        name = "uk_transaction_source_external", columnNames = {"source", "external_id"}))
public class AmlTransaction extends BaseEntity {
    @Column(name = "external_id", nullable = false, length = 100)
    private String externalId;
    @ManyToOne(optional = false, fetch = FetchType.LAZY) @JoinColumn(name = "account_id")
    private Account account;
    @Column(name = "counterparty_account", length = 100)
    private String counterpartyAccount;
    @Column(nullable = false, length = 10)
    private String direction;
    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;
    @JdbcTypeCode(SqlTypes.CHAR) @Column(nullable = false, columnDefinition = "char(3)")
    private String currency;
    @Column(name = "transaction_time", nullable = false)
    private Instant transactionTime;
    @Column(name = "transaction_type", nullable = false, length = 30)
    private String transactionType;
    @Column(nullable = false, length = 20)
    private String status;
    @Column(nullable = false, length = 40)
    private String source;
    @Column(length = 500)
    private String description;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "import_batch_id")
    private TransactionImportBatch importBatch;

    protected AmlTransaction() {}
    public AmlTransaction(String externalId, Account account, String counterpartyAccount, String direction,
                          BigDecimal amount, String currency, Instant transactionTime, String transactionType,
                          String source, String description, TransactionImportBatch importBatch) {
        this.externalId = externalId;
        this.account = account;
        this.counterpartyAccount = counterpartyAccount;
        this.direction = direction;
        this.amount = amount;
        this.currency = currency;
        this.transactionTime = transactionTime;
        this.transactionType = transactionType;
        this.status = "COMPLETED";
        this.source = source;
        this.description = description;
        this.importBatch = importBatch;
    }
    public String getExternalId() { return externalId; }
    public Account getAccount() { return account; }
    public String getCounterpartyAccount() { return counterpartyAccount; }
    public String getDirection() { return direction; }
    public BigDecimal getAmount() { return amount; }
    public String getCurrency() { return currency; }
    public Instant getTransactionTime() { return transactionTime; }
    public String getTransactionType() { return transactionType; }
    public String getStatus() { return status; }
    public String getSource() { return source; }
    public String getDescription() { return description; }
    public TransactionImportBatch getImportBatch() { return importBatch; }
}
