package com.example.aml.transaction;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;

@Service
public class ImportChunkWriter {
    private final TransactionRepository transactions;
    private final TransactionTemplate transaction;

    public ImportChunkWriter(TransactionRepository transactions, PlatformTransactionManager manager) {
        this.transactions = transactions;
        this.transaction = new TransactionTemplate(manager);
        this.transaction.setPropagationBehavior(Propagation.REQUIRES_NEW.value());
    }

    public record Pending(AmlTransaction transaction, long recordNumber) {}
    public record Outcome(int imported, int duplicates, int rejected, List<String> errors) {}

    public Outcome save(List<Pending> chunk) {
        if (chunk.isEmpty()) return new Outcome(0, 0, 0, List.of());
        try {
            transaction.executeWithoutResult(status -> transactions.saveAllAndFlush(
                    chunk.stream().map(Pending::transaction).toList()));
            return new Outcome(chunk.size(), 0, 0, List.of());
        } catch (RuntimeException batchFailure) {
            int imported = 0, duplicates = 0, rejected = 0;
            List<String> errors = new java.util.ArrayList<>();
            for (Pending pending : chunk) {
                try {
                    transaction.executeWithoutResult(status -> transactions.saveAndFlush(pending.transaction()));
                    imported++;
                } catch (RuntimeException rowFailure) {
                    AmlTransaction item = pending.transaction();
                    if (transactions.existsBySourceAndExternalId(item.getSource(), item.getExternalId())) duplicates++;
                    else {
                        rejected++;
                        if (errors.size() < 100) errors.add("第 " + pending.recordNumber() + " 条记录：数据库写入失败");
                    }
                }
            }
            return new Outcome(imported, duplicates, rejected, List.copyOf(errors));
        }
    }
}
