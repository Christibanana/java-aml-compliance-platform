package com.example.aml.transaction;

import com.example.aml.integration.fineract.FineractImportService;
import com.example.aml.transaction.dto.*;
import org.springframework.data.domain.Page;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

@RestController
@RequestMapping("/api/transactions")
public class TransactionController {
    private final TransactionService transactionService;
    private final TransactionImportService importService;
    private final AmlSimImportService amlSimImportService;
    private final FineractImportService fineractImportService;
    public TransactionController(TransactionService transactionService, TransactionImportService importService,
                                 AmlSimImportService amlSimImportService,
                                 FineractImportService fineractImportService) {
        this.transactionService = transactionService;
        this.importService = importService;
        this.amlSimImportService = amlSimImportService;
        this.fineractImportService = fineractImportService;
    }

    @GetMapping @PreAuthorize("hasAuthority('CUSTOMER_READ')")
    public Page<TransactionResponse> list(@RequestParam(required = false) UUID customerId,
                                          @RequestParam(required = false) UUID accountId,
                                          @RequestParam(defaultValue = "0") int page,
                                          @RequestParam(defaultValue = "20") int size) {
        return transactionService.list(customerId, accountId, page, size);
    }

    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('TRANSACTION_IMPORT')")
    public TransactionImportResult importCsv(@RequestPart("file") MultipartFile file) throws IOException {
        return importService.importCsv(file);
    }

    @PostMapping(value = "/import/amlsim", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('TRANSACTION_IMPORT')")
    public TransactionImportResult importAmlSim(@RequestPart("file") MultipartFile file,
                                                @RequestParam(defaultValue = "USD") String currency) throws IOException {
        return amlSimImportService.importCsv(file, currency);
    }

    @PostMapping("/import/fineract/{savingsAccountId}")
    @PreAuthorize("hasAuthority('TRANSACTION_IMPORT')")
    public TransactionImportResult importFineract(@PathVariable long savingsAccountId) {
        return fineractImportService.importSavingsAccount(savingsAccountId);
    }

    @GetMapping("/integrations")
    @PreAuthorize("hasAuthority('TRANSACTION_IMPORT')")
    public java.util.List<java.util.Map<String, Object>> integrations() {
        return java.util.List.of(
                java.util.Map.of("name", "LOCAL_CSV", "available", true, "dataMode", "SIMULATED"),
                java.util.Map.of("name", "AMLSIM_CSV", "available", true, "dataMode", "SIMULATED"),
                java.util.Map.of("name", "FINERACT_API", "available", fineractImportService.available(), "dataMode", "EXTERNAL")
        );
    }

    @GetMapping(value = "/template", produces = "text/csv;charset=UTF-8")
    @PreAuthorize("hasAuthority('TRANSACTION_IMPORT')")
    public ResponseEntity<byte[]> template() {
        String sample = "\uFEFF" + TransactionImportService.HEADER + "\n" +
                "TX-DEMO-001,ACC-DEMO-001,CP-DEMO-001,IN,1000.00,CNY,2026-10-14T08:00:00Z,TRANSFER,模拟交易\n";
        return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=transaction-import-template.csv")
                .body(sample.getBytes(StandardCharsets.UTF_8));
    }

    @GetMapping(value = "/template/amlsim", produces = "text/csv;charset=UTF-8")
    @PreAuthorize("hasAuthority('TRANSACTION_IMPORT')")
    public ResponseEntity<byte[]> amlSimTemplate() {
        String sample = "\uFEFF" + AmlSimImportService.HEADER + "\n" +
                "1,2026-10-14T08:00:00Z,1000.00,TRANSFER,ACC-DEMO-001,AMLSIM-CP-001,false,-1\n";
        return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=amlsim-transactions-template.csv")
                .body(sample.getBytes(StandardCharsets.UTF_8));
    }
}
