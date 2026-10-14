package com.example.aml.casework;

import com.example.aml.audit.AuditService;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.nio.file.*;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;

@Service
public class CaseEvidenceService {
    private static final Map<String, Set<String>> ALLOWED_TYPES = Map.of(
            "application/pdf", Set.of("pdf"),
            "image/png", Set.of("png"),
            "image/jpeg", Set.of("jpg", "jpeg"),
            "text/plain", Set.of("txt", "csv"),
            "text/csv", Set.of("csv"));

    private final JdbcTemplate jdbc;
    private final AuditService audit;
    private final Path storageRoot;
    private final long maxSize;

    public CaseEvidenceService(JdbcTemplate jdbc, AuditService audit,
            @Value("${aml.evidence.storage-root:./data/evidence}") String storageRoot,
            @Value("${aml.evidence.max-size-bytes:10485760}") long maxSize) {
        this.jdbc = jdbc;
        this.audit = audit;
        this.storageRoot = Path.of(storageRoot).toAbsolutePath().normalize();
        this.maxSize = maxSize;
    }

    @PostConstruct
    void initialize() {
        try {
            Files.createDirectories(storageRoot);
        } catch (IOException exception) {
            throw new IllegalStateException("无法初始化案件证据存储目录", exception);
        }
    }

    @Transactional
    public UUID upload(UUID caseId, UUID operatorId, MultipartFile file) {
        requireMutableCase(caseId);
        String originalName = safeFileName(file.getOriginalFilename());
        String contentType = Optional.ofNullable(file.getContentType()).orElse("").toLowerCase(Locale.ROOT);
        validateFile(originalName, contentType, file.getSize());
        UUID evidenceId = UUID.randomUUID();
        Path caseDirectory = safeResolve(caseId.toString());
        Path target = safeResolve(caseId + "/" + evidenceId + ".bin");
        Path temp = null;
        try {
            Files.createDirectories(caseDirectory);
            temp = Files.createTempFile(caseDirectory, evidenceId.toString(), ".upload");
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream input = new DigestInputStream(file.getInputStream(), digest)) {
                Files.copy(input, temp, StandardCopyOption.REPLACE_EXISTING);
            }
            long actualSize = Files.size(temp);
            if (actualSize <= 0 || actualSize > maxSize) throw new IllegalArgumentException("证据文件大小不合法");
            move(temp, target);
            String sha256 = HexFormat.of().formatHex(digest.digest());
            jdbc.update("insert into aml_case_evidence(id,case_id,original_name,storage_key,content_type,size_bytes,"
                            + "sha256,uploaded_by,active) values(?,?,?,?,?,?,?,?,true)", evidenceId, caseId,
                    originalName, caseId + "/" + evidenceId + ".bin", contentType, actualSize, sha256, operatorId);
            deleteOnRollback(target);
            audit.record("UPLOAD_EVIDENCE", "CASE", caseId, "SUCCESS",
                    Map.of("evidenceId", evidenceId, "fileName", originalName, "sha256", sha256));
            return evidenceId;
        } catch (IllegalArgumentException exception) {
            deleteQuietly(temp);
            deleteQuietly(target);
            throw exception;
        } catch (Exception exception) {
            deleteQuietly(temp);
            deleteQuietly(target);
            throw new IllegalStateException("证据文件保存失败", exception);
        }
    }

    @Transactional
    public Download download(UUID caseId, UUID evidenceId) {
        List<Map<String, Object>> rows = jdbc.queryForList("select original_name,content_type,size_bytes,storage_key "
                + "from aml_case_evidence where id=? and case_id=? and active=true", evidenceId, caseId);
        if (rows.isEmpty()) throw new NoSuchElementException("证据文件不存在");
        Map<String, Object> row = rows.getFirst();
        Path path = safeResolve((String) row.get("storage_key"));
        if (!Files.isRegularFile(path)) throw new NoSuchElementException("证据文件内容不存在");
        audit.record("DOWNLOAD_EVIDENCE", "CASE", caseId, "SUCCESS", Map.of("evidenceId", evidenceId));
        return new Download((String) row.get("original_name"), (String) row.get("content_type"),
                ((Number) row.get("size_bytes")).longValue(), path);
    }

    @Transactional
    public void remove(UUID caseId, UUID evidenceId, UUID operatorId, String reason) {
        requireMutableCase(caseId);
        int updated = jdbc.update("update aml_case_evidence set active=false,removed_at=?,removed_by=?,"
                        + "removal_reason=?,updated_at=? where id=? and case_id=? and active=true",
                Timestamp.from(Instant.now()), operatorId, reason.trim(), Timestamp.from(Instant.now()),
                evidenceId, caseId);
        if (updated == 0) throw new NoSuchElementException("证据文件不存在");
        audit.record("REMOVE_EVIDENCE", "CASE", caseId, "SUCCESS",
                Map.of("evidenceId", evidenceId, "reason", reason.trim()));
    }

    private void requireMutableCase(UUID caseId) {
        List<String> statuses = jdbc.query("select status from aml_case where id=? for update",
                (rs, rowNum) -> rs.getString(1), caseId);
        if (statuses.isEmpty()) throw new NoSuchElementException("案件不存在");
        if (CaseService.Status.CLOSED.name().equals(statuses.getFirst())) {
            throw new IllegalArgumentException("已关闭案件不能修改证据");
        }
    }

    private void validateFile(String name, String contentType, long size) {
        if (size <= 0) throw new IllegalArgumentException("证据文件不能为空");
        if (size > maxSize) throw new IllegalArgumentException("证据文件不能超过 " + maxSize + " 字节");
        Set<String> extensions = ALLOWED_TYPES.get(contentType);
        if (extensions == null) throw new IllegalArgumentException("不支持的证据文件类型");
        String extension = name.contains(".") ? name.substring(name.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT) : "";
        if (!extensions.contains(extension)) throw new IllegalArgumentException("证据文件扩展名与类型不匹配");
    }

    private String safeFileName(String supplied) {
        if (supplied == null || supplied.isBlank()) throw new IllegalArgumentException("证据文件名不能为空");
        String normalized = supplied.replace('\\', '/');
        String name = normalized.substring(normalized.lastIndexOf('/') + 1).trim();
        if (name.isBlank() || name.length() > 255 || name.indexOf('\0') >= 0) {
            throw new IllegalArgumentException("证据文件名不合法");
        }
        return name;
    }

    private Path safeResolve(String key) {
        Path resolved = storageRoot.resolve(key).normalize();
        if (!resolved.startsWith(storageRoot)) throw new IllegalStateException("非法证据存储路径");
        return resolved;
    }

    private void move(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException exception) {
            Files.move(source, target);
        }
    }

    private void deleteOnRollback(Path target) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) return;
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCompletion(int status) {
                if (status != TransactionSynchronization.STATUS_COMMITTED) deleteQuietly(target);
            }
        });
    }

    private static void deleteQuietly(Path path) {
        if (path == null) return;
        try { Files.deleteIfExists(path); } catch (IOException ignored) { }
    }

    public record Download(String originalName, String contentType, long size, Path path) {}
}
