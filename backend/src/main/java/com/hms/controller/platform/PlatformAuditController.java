package com.hms.controller.platform;

import com.hms.entity.AuditLog;
import com.hms.service.platform.PlatformHospitalService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * PlatformAuditController - Controller for fetching audit logs
 * 
 * Provides endpoints for Super Admin to view system audit logs.
 * 
 * @author HMS Team
 * @version Phase-1
 */
@RestController
@RequestMapping("/platform/audit-logs")
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class PlatformAuditController {

    @Autowired
    private PlatformHospitalService hospitalService;

    /**
     * Get all audit logs
     * 
     * @return List of audit logs
     */
    @Autowired
    private com.hms.service.AuditLogService auditLogService;

    /**
     * Platform activity. With {@code page}: one page of any period (from/to dates, optional exact
     * action), with totals. Without it: the newest entries as a plain list (unchanged).
     */
    @GetMapping
    public ResponseEntity<?> getAuditLogs(
            @org.springframework.web.bind.annotation.RequestParam(required = false) Integer page,
            @org.springframework.web.bind.annotation.RequestParam(defaultValue = "25") int size,
            @org.springframework.web.bind.annotation.RequestParam(required = false) String action,
            @org.springframework.web.bind.annotation.RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) java.time.LocalDate from,
            @org.springframework.web.bind.annotation.RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) java.time.LocalDate to) {
        if (page != null) {
            return ResponseEntity.ok(com.hms.service.AuditLogService.pageBody(
                    auditLogService.pagePlatformLogs(action, from, to, page, size)));
        }
        List<AuditLog> logs = hospitalService.getAuditLogs();
        return ResponseEntity.ok(logs);
    }

    /** Distinct platform actions, for the paged view's action filter. */
    @GetMapping("/actions")
    public ResponseEntity<List<String>> getActions() {
        return ResponseEntity.ok(auditLogService.platformActions());
    }
}
