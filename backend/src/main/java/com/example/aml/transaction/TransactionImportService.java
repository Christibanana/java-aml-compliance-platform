package com.example.aml.transaction;

import com.example.aml.account.*;
import com.example.aml.audit.AuditService;
import com.example.aml.transaction.dto.TransactionImportResult;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import java.io.*;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.*;

@Service
public class TransactionImportService {
    public static final String HEADER = "externalId,accountNo,counterpartyAccount,direction,amount,currency,transactionTime,transactionType,description";
    private static final String SOURCE = "LOCAL_CSV";
    private final TransactionRepository transactions;
    private final TransactionImportBatchRepository batches;
    private final AccountRepository accounts;
    private final AuditService audit;

    public TransactionImportService(TransactionRepository transactions, TransactionImportBatchRepository batches,
                                    AccountRepository accounts, AuditService audit) {
        this.transactions = transactions;
        this.batches = batches;
        this.accounts = accounts;
        this.audit = audit;
    }

    @Transactional
    public TransactionImportResult importCsv(MultipartFile file) throws IOException {
        validateFile(file);
        String fileName = Objects.requireNonNullElse(file.getOriginalFilename(), "transactions.csv");
        TransactionImportBatch batch = batches.save(new TransactionImportBatch(
                "TXB-" + UUID.randomUUID().toString().replace("-", "").substring(0, 20).toUpperCase(),
                SOURCE, fileName));
        int total = 0, imported = 0, duplicates = 0, rejected = 0;
        List<String> errors = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {
            String header = reader.readLine();
            if (header != null && header.startsWith("\uFEFF")) header = header.substring(1);
            if (!HEADER.equals(header)) throw new IllegalArgumentException("CSV 表头不符合模板");
            String line;
            int lineNo = 1;
            while ((line = reader.readLine()) != null) {
                lineNo++;
                if (line.isBlank()) continue;
                total++;
                try {
                    List<String> columns = parseCsvLine(line);
                    if (columns.size() != 9) throw new IllegalArgumentException("字段数应为 9");
                    String externalId = required(columns.get(0), "外部流水号", 100);
                    if (transactions.existsBySourceAndExternalId(SOURCE, externalId)) {
                        duplicates++;
                        continue;
                    }
                    Account account = accounts.findByAccountNo(required(columns.get(1), "账号", 64))
                            .orElseThrow(() -> new IllegalArgumentException("账号不存在"));
                    if (account.getStatus() != Account.Status.ACTIVE) throw new IllegalArgumentException("账户不是可交易状态");
                    String direction = required(columns.get(3), "方向", 10).toUpperCase();
                    if (!Set.of("IN", "OUT").contains(direction)) throw new IllegalArgumentException("方向应为 IN 或 OUT");
                    BigDecimal amount = parseAmount(columns.get(4));
                    String currency = required(columns.get(5), "币种", 3).toUpperCase();
                    if (!currency.matches("[A-Z]{3}")) throw new IllegalArgumentException("币种应为三位大写代码");
                    if (!currency.equals(account.getCurrency())) throw new IllegalArgumentException("交易币种与账户币种不一致");
                    Instant transactionTime = parseInstant(columns.get(6));
                    String transactionType = required(columns.get(7), "交易类型", 30);
                    String counterparty = optional(columns.get(2), 100, "对手方账号");
                    String description = optional(columns.get(8), 500, "描述");
                    transactions.save(new AmlTransaction(externalId, account, counterparty, direction, amount,
                            currency, transactionTime, transactionType, SOURCE, description, batch));
                    imported++;
                } catch (RuntimeException exception) {
                    rejected++;
                    if (errors.size() < 100) errors.add("第 " + lineNo + " 行：" + exception.getMessage());
                }
            }
        }
        batch.complete(total, imported, rejected, errors.isEmpty() ? null : String.join("\n", errors));
        audit.record("IMPORT", "TRANSACTION", batch.getId(), "SUCCESS",
                Map.of("file", fileName, "imported", imported, "duplicates", duplicates, "rejected", rejected));
        return new TransactionImportResult(batch.getId(), batch.getBatchNo(), total, imported,
                duplicates, rejected, List.copyOf(errors));
    }

    private static void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) throw new IllegalArgumentException("CSV 文件不能为空");
        if (file.getSize() > 10 * 1024 * 1024) throw new IllegalArgumentException("CSV 文件不能超过 10MB");
        String name = file.getOriginalFilename();
        if (name != null && !name.toLowerCase(Locale.ROOT).endsWith(".csv")) {
            throw new IllegalArgumentException("仅支持 CSV 文件");
        }
    }

    static List<String> parseCsvLine(String line) {
        List<String> values = new ArrayList<>();
        StringBuilder value = new StringBuilder();
        boolean quoted = false;
        for (int index = 0; index < line.length(); index++) {
            char character = line.charAt(index);
            if (character == '"') {
                if (quoted && index + 1 < line.length() && line.charAt(index + 1) == '"') {
                    value.append('"');
                    index++;
                } else quoted = !quoted;
            } else if (character == ',' && !quoted) {
                values.add(value.toString().trim());
                value.setLength(0);
            } else value.append(character);
        }
        if (quoted) throw new IllegalArgumentException("引号未闭合");
        values.add(value.toString().trim());
        return values;
    }

    private static String required(String value, String label, int max) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(label + "不能为空");
        String normalized = value.trim();
        if (normalized.length() > max) throw new IllegalArgumentException(label + "超过 " + max + " 个字符");
        return normalized;
    }
    private static String optional(String value, int max, String label) {
        if (value == null || value.isBlank()) return null;
        return required(value, label, max);
    }
    private static BigDecimal parseAmount(String value) {
        try {
            BigDecimal amount = new BigDecimal(required(value, "金额", 30));
            if (amount.signum() <= 0 || amount.scale() > 4 || amount.precision() > 19) {
                throw new IllegalArgumentException("金额必须大于 0，且最多 15 位整数和 4 位小数");
            }
            return amount;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("金额格式不正确");
        }
    }
    private static Instant parseInstant(String value) {
        try { return Instant.parse(required(value, "交易时间", 40)); }
        catch (DateTimeParseException exception) { throw new IllegalArgumentException("交易时间必须为 ISO-8601 UTC 格式"); }
    }
}
