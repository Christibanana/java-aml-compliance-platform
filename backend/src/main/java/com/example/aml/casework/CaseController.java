package com.example.aml.casework;

import com.example.aml.alert.AlertService;
import com.example.aml.casework.dto.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/cases")
@PreAuthorize("hasAuthority('CASE_INVESTIGATE')")
public class CaseController {
    private final CaseService service;
    private final CaseQueryService queries;
    private final CaseEvidenceService evidence;
    private final AlertService alerts;

    public CaseController(CaseService service, CaseQueryService queries,
                          CaseEvidenceService evidence, AlertService alerts) {
        this.service = service;
        this.queries = queries;
        this.evidence = evidence;
        this.alerts = alerts;
    }

    public record NoteRequest(@NotBlank @Size(max = 5000) String content) {}
    public record TransitionRequest(@NotNull CaseService.Status status,
                                    @NotBlank @Size(max = 2000) String comment) {}
    public record AssignRequest(@NotNull UUID userId) {}
    public record RemoveEvidenceRequest(@NotBlank @Size(max = 500) String reason) {}

    @GetMapping
    public CasePage list(@RequestParam(required = false) String status,
                         @RequestParam(required = false) String priority,
                         @RequestParam(required = false) UUID investigatorId,
                         @RequestParam(required = false) String keyword,
                         @RequestParam(defaultValue = "0") int page,
                         @RequestParam(defaultValue = "20") int size) {
        return queries.search(status, priority, investigatorId, keyword, page, size);
    }

    @GetMapping("/investigators")
    public List<InvestigatorSummary> investigators() {
        return queries.investigators();
    }

    @GetMapping("/{id}")
    public CaseDetail get(@PathVariable UUID id) {
        return queries.get(id);
    }

    @PostMapping("/{id}/assign")
    public CaseDetail assign(@PathVariable UUID id, @Valid @RequestBody AssignRequest request) {
        service.assign(id, request.userId(), alerts.currentUserId());
        return queries.get(id);
    }

    @PostMapping("/{id}/notes")
    public CaseDetail addNote(@PathVariable UUID id, @Valid @RequestBody NoteRequest request) {
        service.addNote(id, alerts.currentUserId(), request.content());
        return queries.get(id);
    }

    @PostMapping("/{id}/transition")
    public CaseDetail transition(@PathVariable UUID id, @Valid @RequestBody TransitionRequest request) {
        service.transition(id, request.status(), request.comment(), alerts.currentUserId());
        return queries.get(id);
    }

    @PostMapping(path = "/{id}/evidence", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public CaseDetail uploadEvidence(@PathVariable UUID id, @RequestPart("file") MultipartFile file) {
        evidence.upload(id, alerts.currentUserId(), file);
        return queries.get(id);
    }

    @GetMapping("/{caseId}/evidence/{evidenceId}/download")
    public ResponseEntity<FileSystemResource> downloadEvidence(@PathVariable UUID caseId,
                                                                @PathVariable UUID evidenceId) {
        CaseEvidenceService.Download download = evidence.download(caseId, evidenceId);
        MediaType mediaType;
        try { mediaType = MediaType.parseMediaType(download.contentType()); }
        catch (InvalidMediaTypeException exception) { mediaType = MediaType.APPLICATION_OCTET_STREAM; }
        return ResponseEntity.ok()
                .contentType(mediaType)
                .contentLength(download.size())
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(download.originalName(), StandardCharsets.UTF_8).build().toString())
                .body(new FileSystemResource(download.path()));
    }

    @DeleteMapping("/{caseId}/evidence/{evidenceId}")
    public CaseDetail removeEvidence(@PathVariable UUID caseId, @PathVariable UUID evidenceId,
                                     @Valid @RequestBody RemoveEvidenceRequest request) {
        evidence.remove(caseId, evidenceId, alerts.currentUserId(), request.reason());
        return queries.get(caseId);
    }
}
