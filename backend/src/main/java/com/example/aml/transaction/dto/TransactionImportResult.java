package com.example.aml.transaction.dto;

import java.util.List;
import java.util.UUID;

public record TransactionImportResult(UUID batchId, String batchNo, int total, int imported,
                                      int duplicates, int rejected, List<String> errors) {}
