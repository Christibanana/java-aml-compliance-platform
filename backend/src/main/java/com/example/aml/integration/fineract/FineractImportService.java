package com.example.aml.integration.fineract;

import com.example.aml.account.*;
import com.example.aml.audit.AuditService;
import com.example.aml.transaction.*;
import com.example.aml.transaction.dto.TransactionImportResult;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.util.*;

@Service
public class FineractImportService {
    private static final String SOURCE = "FINERACT";
    private final FineractClient client;
    private final TransactionRepository transactions;
    private final TransactionImportBatchRepository batches;
    private final AccountRepository accounts;
    private final AuditService audit;

    public FineractImportService(FineractClient client, TransactionRepository transactions,
                                 TransactionImportBatchRepository batches, AccountRepository accounts,
                                 AuditService audit) {
        this.client = client;
        this.transactions = transactions;
        this.batches = batches;
        this.accounts = accounts;
        this.audit = audit;
    }

    public boolean available() { return client.available(); }

    @Transactional
    public TransactionImportResult importSavingsAccount(long savingsAccountId) {
        FineractClient.SavingsAccount remote = client.fetchSavingsAccount(savingsAccountId);
        Account account = accounts.findByAccountNo(remote.accountNo())
                .orElseThrow(() -> new IllegalArgumentException("Fineract accountNo 未映射到本地账户：" + remote.accountNo()));
        if (account.getStatus() != Account.Status.ACTIVE) throw new IllegalArgumentException("映射的本地账户不是可交易状态");
        if (!account.getCurrency().equals(remote.currency())) throw new IllegalArgumentException("Fineract 币种与本地账户币种不一致");
        TransactionImportBatch batch = batches.save(new TransactionImportBatch(batchNo(), SOURCE,
                "savings-account-" + savingsAccountId));
        int imported = 0, duplicates = 0, rejected = 0;
        List<String> errors = new ArrayList<>();
        for (FineractClient.Transaction remoteTransaction : remote.transactions()) {
            try {
                String externalId = "SAV-" + remote.id() + "-TX-" + remoteTransaction.id();
                if (transactions.existsBySourceAndExternalId(SOURCE, externalId)) { duplicates++; continue; }
                if (remoteTransaction.reversed()) throw new IllegalArgumentException("已冲正交易不导入");
                String direction;
                if (remoteTransaction.deposit() && !remoteTransaction.withdrawal()) direction = "IN";
                else if (remoteTransaction.withdrawal() && !remoteTransaction.deposit()) direction = "OUT";
                else throw new IllegalArgumentException("不支持的 Fineract 交易方向");
                if (remoteTransaction.amount().signum() <= 0 || remoteTransaction.amount().scale() > 4
                        || remoteTransaction.amount().precision() > 19) throw new IllegalArgumentException("交易金额精度不合法");
                String type = remoteTransaction.typeCode();
                if (type.length() > 30) type = type.substring(0, 30);
                transactions.save(new AmlTransaction(externalId, account, null, direction,
                        remoteTransaction.amount(), remote.currency(),
                        remoteTransaction.date().atStartOfDay(ZoneOffset.UTC).toInstant(), type, SOURCE,
                        "Imported from Apache Fineract savings account API; date-only value normalized to UTC start of day", batch));
                imported++;
            } catch (RuntimeException exception) {
                rejected++;
                if (errors.size() < 100) errors.add("Fineract transaction " + remoteTransaction.id() + "：" + exception.getMessage());
            }
        }
        batch.complete(remote.transactions().size(), imported, rejected, errors.isEmpty() ? null : String.join("\n", errors));
        audit.record("IMPORT", "TRANSACTION", batch.getId(), "SUCCESS",
                Map.of("source", SOURCE, "dataMode", "EXTERNAL", "savingsAccountId", savingsAccountId,
                        "imported", imported, "duplicates", duplicates, "rejected", rejected));
        return new TransactionImportResult(batch.getId(), batch.getBatchNo(), remote.transactions().size(),
                imported, duplicates, rejected, List.copyOf(errors));
    }

    private static String batchNo() { return "TXB-" + UUID.randomUUID().toString().replace("-", "").substring(0, 20).toUpperCase(Locale.ROOT); }
}
