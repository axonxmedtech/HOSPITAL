package com.hms.controller.platform;

import com.hms.dto.backup.*;
import com.hms.service.platform.PlatformBackupService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.security.Principal;
import java.util.Map;

@RestController
@RequestMapping("/platform/backups")
@PreAuthorize("hasRole('SUPER_ADMIN')")
@RequiredArgsConstructor
public class PlatformBackupController {

    private final PlatformBackupService backupService;

    @GetMapping
    public ResponseEntity<BackupListResponse> listBackups() {
        return ResponseEntity.ok(backupService.listBackups());
    }

    @GetMapping("/{fileName}/download")
    public ResponseEntity<Resource> downloadBackup(
            @PathVariable String fileName,
            Principal principal,
            HttpServletRequest request) {

        String userEmail = principal != null ? principal.getName() : "SUPER_ADMIN";
        String clientIp = extractClientIp(request);

        Resource resource = backupService.downloadBackup(fileName, userEmail, clientIp);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fileName + "\"")
                .header(HttpHeaders.CACHE_CONTROL, "no-store, no-cache, must-revalidate")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(resource);
    }

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> uploadBackup(
            @RequestParam("file") MultipartFile file,
            Principal principal,
            HttpServletRequest request) {

        String userEmail = principal != null ? principal.getName() : "SUPER_ADMIN";
        String clientIp = extractClientIp(request);

        backupService.uploadBackup(file, userEmail, clientIp);

        return ResponseEntity.ok(Map.of(
                "message", "Backup uploaded successfully",
                "fileName", file.getOriginalFilename()
        ));
    }

    @PostMapping("/validate-restore")
    public ResponseEntity<BackupValidateResponse> validateRestore(
            @Valid @RequestBody BackupValidateRequest validateRequest,
            Principal principal,
            HttpServletRequest request) {

        String userEmail = principal != null ? principal.getName() : "SUPER_ADMIN";
        String clientIp = extractClientIp(request);

        return ResponseEntity.ok(backupService.validateRestore(validateRequest, userEmail, clientIp));
    }

    @PostMapping("/execute-restore")
    public ResponseEntity<?> executeRestore(
            @Valid @RequestBody BackupRestoreRequest restoreRequest,
            Principal principal,
            HttpServletRequest request) {

        String userEmail = principal != null ? principal.getName() : "SUPER_ADMIN";
        String clientIp = extractClientIp(request);

        backupService.executeLiveRestore(restoreRequest, userEmail, clientIp);

        return ResponseEntity.ok(Map.of(
                "message", "Live database restore initiated successfully"
        ));
    }

    private String extractClientIp(HttpServletRequest request) {
        if (request == null) return "127.0.0.1";
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
