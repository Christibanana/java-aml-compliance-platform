package com.example.aml.dashboard;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;

@RestController
@RequestMapping("/api/reports")
public class ReportController {
    private final ReportExportService service;

    public ReportController(ReportExportService service) { this.service = service; }

    @GetMapping(value = "/{type}/csv", produces = "text/csv")
    @PreAuthorize("hasAuthority('REPORT_READ')")
    public ResponseEntity<byte[]> export(@PathVariable ReportExportService.ReportType type,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        ReportExportService.ExportedReport report = service.export(type, ReportRange.of(from, to));
        ContentDisposition disposition = ContentDisposition.attachment()
                .filename(report.filename(), StandardCharsets.UTF_8).build();
        return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .contentType(MediaType.parseMediaType("text/csv;charset=UTF-8"))
                .header("X-Report-Rows", Integer.toString(report.rows())).body(report.content());
    }
}
