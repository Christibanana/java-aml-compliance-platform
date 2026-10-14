package com.example.aml.transaction;

import com.example.aml.common.BaseEntity;
import jakarta.persistence.*;

@Entity
@Table(name = "aml_transaction_import_batch")
public class TransactionImportBatch extends BaseEntity {
    public enum Status { PROCESSING, COMPLETED, COMPLETED_WITH_ERRORS, FAILED }

    @Column(name = "batch_no", nullable = false, unique = true, length = 50)
    private String batchNo;
    @Column(nullable = false, length = 40)
    private String source;
    @Column(name = "file_name", length = 255)
    private String fileName;
    @Column(name = "total_count", nullable = false)
    private int totalCount;
    @Column(name = "success_count", nullable = false)
    private int successCount;
    @Column(name = "error_count", nullable = false)
    private int errorCount;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 30)
    private Status status;
    @Column(name = "error_details", columnDefinition = "text")
    private String errorDetails;

    protected TransactionImportBatch() {}
    public TransactionImportBatch(String batchNo, String source, String fileName) {
        this.batchNo = batchNo;
        this.source = source;
        this.fileName = fileName;
        this.status = Status.PROCESSING;
    }
    public void complete(int total, int success, int errors, String details) {
        totalCount = total;
        successCount = success;
        errorCount = errors;
        errorDetails = details;
        status = errors == 0 ? Status.COMPLETED : Status.COMPLETED_WITH_ERRORS;
    }
    public String getBatchNo() { return batchNo; }
    public Status getStatus() { return status; }
}
