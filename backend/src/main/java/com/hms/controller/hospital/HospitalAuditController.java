package com.hms.controller.hospital;

import com.hms.entity.AuditLog;

import com.hms.service.AuditLogService;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Controller for retrieving Audit Logs for Hospital Admins.
 */
@RestController
@RequestMapping("/hospital/audit-logs")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
@PreAuthorize("hasRole('HOSPITAL_ADMIN')")
public class HospitalAuditController {

    private final AuditLogService auditLogService;
    private final com.hms.security.SecurityContextHelper securityHelper;

    /**
     * Get all recent activity for the current hospital.
     */
    @GetMapping
    public ResponseEntity<List<AuditLog>> getRecentActivity(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String role,
            @RequestParam(required = false) Integer limit) {
        try {
            Long hospitalId = securityHelper.getCurrentHospitalId();

            if (hospitalId == null) {
                System.err.println("WARN: Hospital ID is null - returning empty activity feed");
                return ResponseEntity.ok(java.util.Collections.emptyList());
            }

            String mappedRole = null;
            if (role != null && !role.trim().isEmpty() && !"ALL".equalsIgnoreCase(role)) {
                mappedRole = role.trim();
            }

            List<AuditLog> logs = auditLogService.getLogsByHospitalId(hospitalId, search, mappedRole);
            if (limit != null && logs.size() > limit) {
                logs = logs.subList(0, limit);
            }
            return ResponseEntity.ok(logs);
        } catch (Exception e) {
            System.err.println("WARN: Could not retrieve hospital context - returning empty activity feed");
            System.err.println("Error: " + e.getMessage());
            return ResponseEntity.ok(java.util.Collections.emptyList());
        }
    }

    /**
     * Get history for a specific entity.
     */
    @GetMapping("/{entityType}/{entityId}")
    public ResponseEntity<List<AuditLog>> getEntityHistory(
            @PathVariable String entityType,
            @PathVariable String entityId) {
        try {
            Long hospitalId = securityHelper.getCurrentHospitalId();
            if (hospitalId == null) {
                return ResponseEntity.ok(java.util.Collections.emptyList());
            }
            List<AuditLog> logs = auditLogService.getLogsByEntityAndHospitalId(entityType.toUpperCase(), entityId, hospitalId);
            return ResponseEntity.ok(logs);
        } catch (Exception e) {
            return ResponseEntity.ok(java.util.Collections.emptyList());
        }
    }
}
