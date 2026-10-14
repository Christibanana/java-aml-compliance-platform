package com.example.aml.transaction;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface TransactionImportBatchRepository extends JpaRepository<TransactionImportBatch, UUID> {}
