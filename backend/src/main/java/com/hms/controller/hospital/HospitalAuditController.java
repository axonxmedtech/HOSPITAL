package com.hms.controller.hospital;

import com.hms.entity.AuditLog;

import com.hms.service.AuditLogService;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Controller for retrieving Audit Logs for Hospital Admins.
 */
@RestController
@RequestMapping("/hospital/audit-logs")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class HospitalAuditController {

    private final AuditLogService auditLogService;
    private final com.hms.security.SecurityContextHelper securityHelper;

    /**
     * Get all recent activity for the current hospital.
     */
    @GetMapping
    public ResponseEntity<List<AuditLog>> getRecentActivity() {
        try {
            Long hospitalId = securityHelper.getCurrentHospitalId();

            if (hospitalId == null) {
                System.err.println("WARN: Hospital ID is null - returning empty activity feed");
                return ResponseEntity.ok(java.util.Collections.emptyList());
            }

            List<AuditLog> logs = auditLogService.getLogsByHospitalId(hospitalId);
            // Return only the last 20 for the Dashboard feed
            if (logs.size() > 20) {
                logs = logs.subList(0, 20);
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
        // Security check: Ensure the user belongs to the same hospital as the entity
        // (Optional implementation detail)
        // For Phase-1, valid authenticated admin access is sufficient.

        List<AuditLog> logs = auditLogService.getLogsByEntity(entityType.toUpperCase(), entityId);
        return ResponseEntity.ok(logs);
    }
}
