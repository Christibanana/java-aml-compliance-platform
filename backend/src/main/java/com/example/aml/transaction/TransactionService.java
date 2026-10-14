package com.example.aml.transaction;

import com.example.aml.transaction.dto.TransactionResponse;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;

@Service
public class TransactionService {
    private final TransactionRepository transactions;
    public TransactionService(TransactionRepository transactions) { this.transactions = transactions; }

    @Transactional(readOnly = true)
    public Page<TransactionResponse> list(UUID customerId, UUID accountId, int page, int size) {
        if (customerId != null && accountId != null) throw new IllegalArgumentException("客户与账户筛选不能同时提交");
        PageRequest pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100),
                Sort.by(Sort.Direction.DESC, "transactionTime"));
        Page<AmlTransaction> result = accountId != null ? transactions.findByAccount_Id(accountId, pageable)
                : customerId != null ? transactions.findByCustomerId(customerId, pageable)
                : transactions.findAll(pageable);
        return result.map(TransactionResponse::from);
    }
}
