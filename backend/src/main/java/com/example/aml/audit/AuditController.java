package com.example.aml.audit;

import com.example.aml.dashboard.ReportRange;
import jakarta.validation.constraints.*;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;

@Validated
@RestController
@RequestMapping("/api/audit-logs")
public class AuditController {
    private final AuditQueryService service;

    public AuditController(AuditQueryService service) { this.service = service; }

    @GetMapping
    @PreAuthorize("hasAuthority('AUDIT_READ')")
    public AuditQueryService.AuditPage list(
            @RequestParam(required = false) @Size(max = 80) String actor,
            @RequestParam(required = false) @Size(max = 80) String action,
            @RequestParam(required = false) @Size(max = 80) String resourceType,
            @RequestParam(required = false) @Size(max = 20) String result,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return service.query(new AuditQueryService.AuditFilter(actor, action, resourceType, result,
                ReportRange.of(from, to), page, size));
    }

    @GetMapping(value = "/export", produces = "text/csv")
    @PreAuthorize("hasAuthority('AUDIT_READ')")
    public ResponseEntity<byte[]> export(
            @RequestParam(required = false) @Size(max = 80) String actor,
            @RequestParam(required = false) @Size(max = 80) String action,
            @RequestParam(required = false) @Size(max = 80) String resourceType,
            @RequestParam(required = false) @Size(max = 20) String result,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        AuditQueryService.AuditExport export = service.export(new AuditQueryService.AuditFilter(actor, action,
                resourceType, result, ReportRange.of(from, to), 0, 100));
        ContentDisposition disposition = ContentDisposition.attachment()
                .filename(export.filename(), StandardCharsets.UTF_8).build();
        return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .contentType(MediaType.parseMediaType("text/csv;charset=UTF-8"))
                .header("X-Report-Rows", Integer.toString(export.rows())).body(export.content());
    }
}
