package com.hms.controller.platform;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hms.dto.backup.*;
import com.hms.security.HospitalWebSocketHandler;
import com.hms.security.JwtUtil;
import com.hms.security.SecurityContextHelper;
import com.hms.service.AuditLogService;
import com.hms.service.hospital.MedicineService;
import com.hms.service.platform.PlatformBackupService;
import com.hms.service.platform.PlatformHospitalService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(PlatformBackupController.class)
@Import(PlatformBackupControllerTest.MethodSecurityTestConfig.class)
class PlatformBackupControllerTest {

    @MockBean private com.hms.repository.UserRepository r1bUserRepository;
    @MockBean private com.hms.repository.HospitalRepository r1bHospitalRepository;

    @TestConfiguration
    @EnableMethodSecurity
    static class MethodSecurityTestConfig {}

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @MockBean private PlatformBackupService backupService;
    @MockBean private PlatformHospitalService hospitalService;
    @MockBean private JwtUtil jwtUtil;
    @MockBean private AuditLogService auditLogService;
    @MockBean private SecurityContextHelper securityHelper;
    @MockBean private HospitalWebSocketHandler webSocketHandler;
    @MockBean private MedicineService medicineService;

    // ─── GET /platform/backups ─────────────────────────────────────────────

    @Test
    @WithMockUser(roles = "SUPER_ADMIN")
    void listBackups_returnsOkForSuperAdmin() throws Exception {
        BackupMetadataDto item = BackupMetadataDto.builder()
                .fileName("hms-production-20261124.sql.gz.enc")
                .createdAt(LocalDateTime.now())
                .sizeBytes(1024L)
                .sha256("abc123sha")
                .verified(true)
                .build();

        BackupListResponse response = BackupListResponse.builder()
                .backups(List.of(item))
                .daysSinceLastDownload(2L)
                .hasOverdueDownload(false)
                .build();

        when(backupService.listBackups()).thenReturn(response);

        mockMvc.perform(get("/platform/backups"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.backups[0].fileName").value("hms-production-20261124.sql.gz.enc"))
                .andExpect(jsonPath("$.daysSinceLastDownload").value(2))
                .andExpect(jsonPath("$.hasOverdueDownload").value(false));
    }

    @Test
    @WithMockUser(roles = "HOSPITAL_ADMIN")
    void listBackups_returnsForbiddenForHospitalAdmin() throws Exception {
        mockMvc.perform(get("/platform/backups"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "DOCTOR")
    void listBackups_returnsForbiddenForDoctor() throws Exception {
        mockMvc.perform(get("/platform/backups"))
                .andExpect(status().isForbidden());
    }

    // ─── GET /platform/backups/{fileName}/download ─────────────────────────

    @Test
    @WithMockUser(roles = "SUPER_ADMIN", username = "superadmin@hms.com")
    void downloadBackup_returnsFileForSuperAdmin() throws Exception {
        ByteArrayResource resource = new ByteArrayResource("encrypted-backup-bytes".getBytes());

        when(backupService.downloadBackup(eq("hms-backup.sql.gz.enc"), eq("superadmin@hms.com"), any()))
                .thenReturn(resource);

        mockMvc.perform(get("/platform/backups/hms-backup.sql.gz.enc/download"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"hms-backup.sql.gz.enc\""))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store, no-cache, must-revalidate"))
                .andExpect(content().bytes("encrypted-backup-bytes".getBytes()));
    }

    @Test
    @WithMockUser(roles = "HOSPITAL_ADMIN")
    void downloadBackup_returnsForbiddenForHospitalAdmin() throws Exception {
        mockMvc.perform(get("/platform/backups/hms-backup.sql.gz.enc/download"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "SUPER_ADMIN", username = "superadmin@hms.com")
    void downloadBackup_pathTraversalRefused() throws Exception {
        when(backupService.downloadBackup(eq("../secret.sql.gz.enc"), any(), any()))
                .thenThrow(new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid backup filename"));

        mockMvc.perform(get("/platform/backups/../secret.sql.gz.enc/download"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(roles = "SUPER_ADMIN", username = "superadmin@hms.com")
    void downloadBackup_rateLimitEnforced_returns429() throws Exception {
        when(backupService.downloadBackup(eq("hms-backup.sql.gz.enc"), any(), any()))
                .thenThrow(new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Download limit exceeded"));

        mockMvc.perform(get("/platform/backups/hms-backup.sql.gz.enc/download"))
                .andExpect(status().isTooManyRequests());
    }

    // ─── POST /platform/backups/upload ─────────────────────────────────────

    @Test
    @WithMockUser(roles = "SUPER_ADMIN")
    void uploadBackup_successForSuperAdmin() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "hms-backup.sql.gz.enc",
                MediaType.APPLICATION_OCTET_STREAM_VALUE,
                "dummy-data".getBytes()
        );

        mockMvc.perform(multipart("/platform/backups/upload").file(file).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Backup uploaded successfully"))
                .andExpect(jsonPath("$.fileName").value("hms-backup.sql.gz.enc"));
    }

    @Test
    @WithMockUser(roles = "DOCTOR")
    void uploadBackup_forbiddenForDoctor() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "hms-backup.sql.gz.enc", MediaType.APPLICATION_OCTET_STREAM_VALUE, "dummy".getBytes()
        );

        mockMvc.perform(multipart("/platform/backups/upload").file(file).with(csrf()))
                .andExpect(status().isForbidden());
    }

    // ─── POST /platform/backups/validate-restore ───────────────────────────

    @Test
    @WithMockUser(roles = "SUPER_ADMIN")
    void validateRestore_successForSuperAdmin() throws Exception {
        BackupValidateRequest request = new BackupValidateRequest("hms-backup.sql.gz.enc", "secretKey123");
        BackupValidateResponse response = BackupValidateResponse.builder()
                .valid(true)
                .tableCount(45)
                .coreTablesPresent(true)
                .message("Backup verified successfully")
                .build();

        when(backupService.validateRestore(any(), any(), any())).thenReturn(response);

        mockMvc.perform(post("/platform/backups/validate-restore")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(true))
                .andExpect(jsonPath("$.tableCount").value(45));
    }

    // ─── POST /platform/backups/execute-restore ────────────────────────────

    @Test
    @WithMockUser(roles = "SUPER_ADMIN")
    void executeRestore_successForSuperAdmin() throws Exception {
        BackupRestoreRequest request = new BackupRestoreRequest(
                "hms-backup.sql.gz.enc", "secretKey123", "RESTORE-LIVE-DATABASE"
        );

        mockMvc.perform(post("/platform/backups/execute-restore")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Live database restore initiated successfully"));
    }

    @Test
    @WithMockUser(roles = "SUPER_ADMIN")
    void executeRestore_failsOnInvalidConfirmation() throws Exception {
        BackupRestoreRequest request = new BackupRestoreRequest(
                "hms-backup.sql.gz.enc", "secretKey123", "WRONG_CONFIRMATION"
        );

        doThrow(new ResponseStatusException(HttpStatus.BAD_REQUEST, "Confirmation mismatch"))
                .when(backupService).executeLiveRestore(any(), any(), any());

        mockMvc.perform(post("/platform/backups/execute-restore")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }
}
