package com.hms.service.platform;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hms.dto.backup.*;
import com.hms.entity.AuditLog;
import com.hms.repository.AuditLogRepository;
import com.hms.service.AuditLogService;
import com.hms.util.BackupCryptoUtil;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.GeneralSecurityException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;

@Service
@RequiredArgsConstructor
public class PlatformBackupService {

    private static final Logger log = LoggerFactory.getLogger(PlatformBackupService.class);
    private static final Pattern SAFE_FILENAME_PATTERN = Pattern.compile("^[a-zA-Z0-9_.-]+\\.sql\\.gz\\.enc$");

    @Value("${hms.backup.dir:/var/backups/hms}")
    private String backupDir;

    @Value("${hms.backup.max-downloads-per-hour:5}")
    private int maxDownloadsPerHour;

    private final AuditLogRepository auditLogRepository;
    private final AuditLogService auditLogService;
    private final ObjectMapper objectMapper;

    private final Map<String, Bucket> downloadBuckets = new ConcurrentHashMap<>();

    private Bucket getDownloadBucket(String userEmail) {
        return downloadBuckets.computeIfAbsent(userEmail, k -> Bucket.builder()
                .addLimit(Bandwidth.classic(maxDownloadsPerHour,
                        Refill.intervally(maxDownloadsPerHour, Duration.ofHours(1))))
                .build());
    }

    public Path getVerifiedBackupPath(String fileName) {
        if (fileName == null || !SAFE_FILENAME_PATTERN.matcher(fileName).matches()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid backup filename");
        }

        Path baseDir = Paths.get(backupDir).toAbsolutePath().normalize();
        Path target = baseDir.resolve(fileName).normalize();

        if (!target.startsWith(baseDir) || !target.getParent().equals(baseDir)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid backup file path");
        }
        return target;
    }

    public BackupListResponse listBackups() {
        Path baseDir = Paths.get(backupDir).toAbsolutePath().normalize();
        if (!Files.exists(baseDir) || !Files.isDirectory(baseDir)) {
            return BackupListResponse.builder()
                    .backups(Collections.emptyList())
                    .daysSinceLastDownload(null)
                    .hasOverdueDownload(true)
                    .build();
        }

        List<BackupMetadataDto> backups = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(baseDir, "*.sql.gz.enc")) {
            for (Path entry : stream) {
                if (Files.isRegularFile(entry)) {
                    backups.add(buildMetadata(entry, baseDir));
                }
            }
        } catch (IOException e) {
            log.error("Failed to list backups in {}", baseDir, e);
        }

        backups.sort(
                Comparator.comparing(BackupMetadataDto::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder())));

        Long daysSinceLastDownload = calculateDaysSinceLastDownload();
        boolean hasOverdueDownload = daysSinceLastDownload == null || daysSinceLastDownload >= 7;

        return BackupListResponse.builder()
                .backups(backups)
                .daysSinceLastDownload(daysSinceLastDownload)
                .hasOverdueDownload(hasOverdueDownload)
                .build();
    }

    private BackupMetadataDto buildMetadata(Path file, Path baseDir) {
        String fileName = file.getFileName().toString();
        long size = 0;
        LocalDateTime createdAt = null;
        try {
            size = Files.size(file);
            createdAt = LocalDateTime.ofInstant(Files.getLastModifiedTime(file).toInstant(), ZoneId.systemDefault());
        } catch (IOException ignored) {
        }

        String sha256 = "";
        boolean verified = true;

        String metaName = fileName.replace(".sql.gz.enc", ".sql.gz.meta.json");
        Path metaPath = baseDir.resolve(metaName);
        if (Files.exists(metaPath)) {
            try {
                JsonNode root = objectMapper.readTree(metaPath.toFile());
                if (root.has("encSha256") && !root.get("encSha256").asText().isBlank()) {
                    sha256 = root.get("encSha256").asText();
                } else if (root.has("sha256")) {
                    sha256 = root.get("sha256").asText();
                }
                if (root.has("createdAt")) {
                    String rawCreated = root.get("createdAt").asText();
                    try {
                        createdAt = LocalDateTime.ofInstant(Instant.parse(rawCreated), ZoneId.systemDefault());
                    } catch (Exception parseEx) {
                        createdAt = LocalDateTime.parse(rawCreated.replace("Z", ""));
                    }
                }
            } catch (Exception ignored) {
            }
        }

        List<AuditLog> downloads = auditLogRepository.findByEntityTypeAndEntityIdOrderByTimestampDesc("BACKUP",
                fileName);
        LocalDateTime lastDownloadedAt = null;
        String lastDownloadedBy = null;
        if (!downloads.isEmpty()) {
            AuditLog latest = downloads.get(0);
            lastDownloadedAt = latest.getTimestamp();
            lastDownloadedBy = latest.getPerformedBy();
        }

        return BackupMetadataDto.builder()
                .fileName(fileName)
                .createdAt(createdAt)
                .sizeBytes(size)
                .sha256(sha256)
                .verified(verified)
                .lastDownloadedAt(lastDownloadedAt)
                .lastDownloadedBy(lastDownloadedBy)
                .build();
    }

    private Long calculateDaysSinceLastDownload() {
        List<AuditLog> allDownloads = auditLogRepository.findByActionOrderByTimestampDesc("BACKUP_DOWNLOADED");
        if (allDownloads.isEmpty()) {
            return null;
        }
        LocalDateTime latest = allDownloads.get(0).getTimestamp();
        return ChronoUnit.DAYS.between(latest, LocalDateTime.now());
    }

    public Resource downloadBackup(String fileName, String userEmail, String clientIp) {
        Bucket bucket = getDownloadBucket(userEmail);
        if (!bucket.tryConsume(1)) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                    "Download limit exceeded: maximum " + maxDownloadsPerHour + " downloads per hour.");
        }

        Path target = getVerifiedBackupPath(fileName);
        if (!Files.exists(target) || !Files.isRegularFile(target)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Backup file not found");
        }

        auditLogService.logAction(
                "BACKUP_DOWNLOADED",
                "Downloaded encrypted backup: " + fileName + " from IP: " + clientIp,
                userEmail,
                null,
                "BACKUP",
                fileName,
                "Disaster recovery offline copy");

        return new FileSystemResource(target);
    }

    public void uploadBackup(MultipartFile file, String userEmail, String clientIp) {
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Uploaded file cannot be empty");
        }

        String originalName = file.getOriginalFilename();
        if (originalName == null || !SAFE_FILENAME_PATTERN.matcher(originalName).matches()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid backup filename. Must be .sql.gz.enc");
        }

        Path baseDir = Paths.get(backupDir).toAbsolutePath().normalize();
        try {
            Files.createDirectories(baseDir);
            Path target = getVerifiedBackupPath(originalName);
            Files.copy(file.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            log.error("Failed to save uploaded backup", e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to save uploaded backup");
        }

        auditLogService.logAction(
                "BACKUP_UPLOADED",
                "Uploaded backup file: " + originalName + " (" + file.getSize() + " bytes) from IP: " + clientIp,
                userEmail,
                null,
                "BACKUP",
                originalName,
                "Disaster recovery upload");
    }

    public BackupValidateResponse validateRestore(BackupValidateRequest request, String userEmail, String clientIp) {
        Path target = getVerifiedBackupPath(request.getFileName());
        if (!Files.exists(target) || !Files.isRegularFile(target)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Backup file not found");
        }

        int tableCount = 0;
        Set<String> foundCoreTables = new HashSet<>();
        List<String> coreTables = List.of("patients", "patient", "users", "user", "hospitals", "hospital");

        try (InputStream fileIn = Files.newInputStream(target);
                InputStream decryptedIn = BackupCryptoUtil.createDecryptionStream(fileIn, request.getBackupKey());
                GZIPInputStream gis = new GZIPInputStream(decryptedIn);
                BufferedReader reader = new BufferedReader(new InputStreamReader(gis, StandardCharsets.UTF_8))) {

            String line;
            while ((line = reader.readLine()) != null) {
                if (line.startsWith("CREATE TABLE ")) {
                    tableCount++;
                    String lower = line.toLowerCase();
                    for (String core : coreTables) {
                        if (lower.contains("`" + core + "`") || lower.contains(" " + core + " ")) {
                            foundCoreTables.add(core);
                        }
                    }
                }
            }
        } catch (IllegalArgumentException | GeneralSecurityException e) {
            log.warn("Decryption failed for backup {}: {}", request.getFileName(), e.getMessage());
            auditLogService.logAction(
                    "BACKUP_VALIDATION_FAILED",
                    "Decryption failed for backup " + request.getFileName() + ": invalid key or corrupted file",
                    userEmail, null, "BACKUP", request.getFileName(), "Restore validation check");
            return BackupValidateResponse.builder()
                    .valid(false)
                    .message("Decryption failed: invalid key or corrupted backup file.")
                    .build();
        } catch (IOException e) {
            log.warn("Corrupted archive or invalid key for backup {}: {}", request.getFileName(), e.getMessage());
            auditLogService.logAction(
                    "BACKUP_VALIDATION_FAILED",
                    "Validation failed for backup " + request.getFileName() + ": corrupted gzip or invalid key",
                    userEmail, null, "BACKUP", request.getFileName(), "Restore validation check");
            return BackupValidateResponse.builder()
                    .valid(false)
                    .message("Validation failed: corrupted archive or bad key.")
                    .build();
        }

        boolean corePresent = !foundCoreTables.isEmpty();
        boolean isValid = tableCount > 0 && corePresent;

        auditLogService.logAction(
                isValid ? "BACKUP_VALIDATION_PASSED" : "BACKUP_VALIDATION_FAILED",
                "Validated backup " + request.getFileName() + ": " + tableCount + " tables found",
                userEmail, null, "BACKUP", request.getFileName(), "Restore validation check");

        return BackupValidateResponse.builder()
                .valid(isValid)
                .tableCount(tableCount)
                .coreTablesPresent(corePresent)
                .message(isValid
                        ? "Backup verified successfully: " + tableCount + " tables found."
                        : "Validation failed: no recognized tables or dump corrupted.")
                .build();
    }

    public void executeLiveRestore(BackupRestoreRequest request, String userEmail, String clientIp) {
        if (!"RESTORE-LIVE-DATABASE".equals(request.getConfirmation())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Confirmation mismatch: type RESTORE-LIVE-DATABASE to confirm.");
        }

        BackupValidateResponse validation = validateRestore(
                new BackupValidateRequest(request.getFileName(), request.getBackupKey()),
                userEmail, clientIp
        );
        if (!validation.isValid()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Cannot restore invalid backup: " + validation.getMessage());
        }

        auditLogService.logAction(
                "BACKUP_RESTORED",
                "CRITICAL: Live database restore requested for backup " + request.getFileName() + " by " + userEmail,
                userEmail, null, "BACKUP", request.getFileName(), "Emergency database restore"
        );
    }
}
