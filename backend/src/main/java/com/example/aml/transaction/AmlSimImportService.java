package com.example.aml.transaction;

import com.example.aml.account.*;
import com.example.aml.audit.AuditService;
import com.example.aml.transaction.dto.TransactionImportResult;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.*;

@Service
public class AmlSimImportService {
    public static final String HEADER = "tran_id,tran_timestamp,base_amt,tx_type,orig_acct,bene_acct,is_sar,alert_id";
    private static final Set<String> REQUIRED_COLUMNS = Set.of(
            "tran_id", "tran_timestamp", "base_amt", "tx_type", "orig_acct", "bene_acct", "is_sar", "alert_id");
    private static final String SOURCE = "AMLSIM";
    private final TransactionRepository transactions;
    private final TransactionImportBatchRepository batches;
    private final AccountRepository accounts;
    private final AuditService audit;
    private final ImportChunkWriter writer;

    public AmlSimImportService(TransactionRepository transactions, TransactionImportBatchRepository batches,
                               AccountRepository accounts, AuditService audit, ImportChunkWriter writer) {
        this.transactions = transactions;
        this.batches = batches;
        this.accounts = accounts;
        this.audit = audit;
        this.writer = writer;
    }

    public TransactionImportResult importCsv(MultipartFile file, String currency) throws IOException {
        validateFile(file);
        String normalizedCurrency = Objects.requireNonNullElse(currency, "USD").trim().toUpperCase(Locale.ROOT);
        if (!normalizedCurrency.matches("[A-Z]{3}")) throw new IllegalArgumentException("币种必须为三位大写代码");
        String fileName = Objects.requireNonNullElse(file.getOriginalFilename(), "amlsim-transactions.csv");
        TransactionImportBatch batch = batches.save(new TransactionImportBatch(batchNo(), SOURCE, fileName));
        int total = 0, imported = 0, duplicates = 0, rejected = 0;
        List<String> errors = new ArrayList<>();
        List<ImportChunkWriter.Pending> pending = new ArrayList<>(100);
        Set<String> seen = new HashSet<>();
        try (CSVParser parser = CsvInput.open(file)) {
            Iterator<CSVRecord> records = parser.iterator();
            List<String> header = records.hasNext() ? records.next().toList() : List.of();
            Map<String, Integer> index = index(header);
            if (!index.keySet().containsAll(REQUIRED_COLUMNS)) {
                throw new IllegalArgumentException("AMLSim CSV 缺少官方交易列：" + String.join(",", REQUIRED_COLUMNS));
            }
            while (records.hasNext()) {
                CSVRecord record = records.next();
                total++;
                try {
                    List<String> columns = record.toList();
                    String transactionId = required(value(columns, index, "tran_id"), "tran_id", 80);
                    String externalId = "AMLSIM-" + transactionId;
                    if (seen.contains(externalId) || transactions.existsBySourceAndExternalId(SOURCE, externalId)) { duplicates++; continue; }
                    String origin = required(value(columns, index, "orig_acct"), "orig_acct", 100);
                    String beneficiary = required(value(columns, index, "bene_acct"), "bene_acct", 100);
                    Account account = accounts.findByAccountNo(origin).orElse(null);
                    String direction = "OUT";
                    String counterparty = beneficiary;
                    if (account == null) {
                        account = accounts.findByAccountNo(beneficiary)
                                .orElseThrow(() -> new IllegalArgumentException("orig_acct 和 bene_acct 均未映射到本地账户"));
                        direction = "IN";
                        counterparty = origin;
                    }
                    if (account.getStatus() != Account.Status.ACTIVE) throw new IllegalArgumentException("本地账户不是可交易状态");
                    if (!account.getCurrency().equals(normalizedCurrency)) throw new IllegalArgumentException("指定币种与本地账户币种不一致");
                    String type = required(value(columns, index, "tx_type"), "tx_type", 30);
                    String sar = required(value(columns, index, "is_sar"), "is_sar", 10);
                    String alertId = optional(value(columns, index, "alert_id"), 60, "alert_id");
                    String description = "AMLSim synthetic transaction; is_sar=" + sar + "; alert_id=" + Objects.requireNonNullElse(alertId, "");
                    pending.add(new ImportChunkWriter.Pending(new AmlTransaction(externalId, account, counterparty,
                            direction, parseAmount(value(columns, index, "base_amt")), normalizedCurrency,
                            parseInstant(value(columns, index, "tran_timestamp")), type, SOURCE, description, batch),
                            record.getRecordNumber()));
                    seen.add(externalId);
                } catch (RuntimeException exception) {
                    rejected++;
                    if (errors.size() < 100) errors.add("第 " + record.getRecordNumber() + " 条记录：" + exception.getMessage());
                }
                if (pending.size() == 100) {
                    ImportChunkWriter.Outcome outcome = writer.save(List.copyOf(pending));
                    imported += outcome.imported(); duplicates += outcome.duplicates(); rejected += outcome.rejected();
                    appendErrors(errors, outcome.errors());
                    pending.clear();
                }
            }
        } catch (IOException exception) {
            batch.fail(total, imported, rejected + 1, "CSV 读取失败");
            batches.save(batch);
            throw exception;
        } catch (IllegalArgumentException exception) {
            batch.fail(exception.getMessage());
            batches.save(batch);
            throw exception;
        } catch (RuntimeException exception) {
            batch.fail(total, imported, rejected + 1, "CSV 导入中断：" + exception.getClass().getSimpleName());
            batches.save(batch);
            throw exception;
        }
        ImportChunkWriter.Outcome finalOutcome = writer.save(pending);
        imported += finalOutcome.imported(); duplicates += finalOutcome.duplicates(); rejected += finalOutcome.rejected();
        appendErrors(errors, finalOutcome.errors());
        batch.complete(total, imported, rejected, errors.isEmpty() ? null : String.join("\n", errors));
        batches.save(batch);
        audit.record("IMPORT", "TRANSACTION", batch.getId(), "SUCCESS",
                Map.of("source", SOURCE, "dataMode", "SIMULATED", "file", fileName,
                        "imported", imported, "duplicates", duplicates, "rejected", rejected));
        return new TransactionImportResult(batch.getId(), batch.getBatchNo(), total, imported, duplicates, rejected, List.copyOf(errors));
    }

    private static Map<String, Integer> index(List<String> header) {
        Map<String, Integer> result = new LinkedHashMap<>();
        for (int i = 0; i < header.size(); i++) result.put(header.get(i).trim().toLowerCase(Locale.ROOT), i);
        return result;
    }
    private static void appendErrors(List<String> target, List<String> source) {
        source.stream().limit(Math.max(0, 100 - target.size())).forEach(target::add);
    }
    private static String value(List<String> columns, Map<String, Integer> index, String name) {
        int position = index.get(name);
        if (position >= columns.size()) throw new IllegalArgumentException("字段数少于表头");
        return columns.get(position);
    }
    private static String batchNo() { return "TXB-" + UUID.randomUUID().toString().replace("-", "").substring(0, 20).toUpperCase(Locale.ROOT); }
    private static void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) throw new IllegalArgumentException("AMLSim CSV 文件不能为空");
        if (file.getSize() > 10 * 1024 * 1024) throw new IllegalArgumentException("AMLSim CSV 文件不能超过 10MB");
        String name = file.getOriginalFilename();
        if (name != null && !name.toLowerCase(Locale.ROOT).endsWith(".csv")) throw new IllegalArgumentException("仅支持 CSV 文件");
    }
    private static String required(String value, String label, int max) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(label + " 不能为空");
        String normalized = value.trim();
        if (normalized.length() > max) throw new IllegalArgumentException(label + " 超过 " + max + " 个字符");
        return normalized;
    }
    private static String optional(String value, int max, String label) { return value == null || value.isBlank() ? null : required(value, label, max); }
    private static BigDecimal parseAmount(String value) {
        try {
            BigDecimal amount = new BigDecimal(required(value, "base_amt", 30));
            if (amount.signum() <= 0 || amount.scale() > 4 || amount.precision() > 19) throw new IllegalArgumentException("base_amt 精度不合法");
            return amount;
        } catch (NumberFormatException exception) { throw new IllegalArgumentException("base_amt 格式不正确"); }
    }
    private static Instant parseInstant(String value) {
        try { return Instant.parse(required(value, "tran_timestamp", 40)); }
        catch (DateTimeParseException exception) { throw new IllegalArgumentException("tran_timestamp 必须为 ISO-8601 UTC 格式"); }
    }
}
