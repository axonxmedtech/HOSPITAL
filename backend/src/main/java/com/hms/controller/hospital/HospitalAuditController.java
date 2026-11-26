package com.hms.controller.hospital;

import com.hms.entity.AuditLog;

import com.hms.service.AuditLogService;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Controller for retrieving Audit Logs for Hospital Admins.
 */
@RestController
@RequestMapping({"/hospital/audit-logs", "/clinic/audit-logs", "/pharmacy/audit-logs"})
@RequiredArgsConstructor
@PreAuthorize("hasRole('HOSPITAL_ADMIN')")
public class HospitalAuditController {

    private static final Logger log = LoggerFactory.getLogger(HospitalAuditController.class);

    private final AuditLogService auditLogService;
    private final com.hms.security.SecurityContextHelper securityHelper;

    /**
     * Get all recent activity for the current hospital.
     */
    @GetMapping
    public ResponseEntity<?> getRecentActivity(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String role,
            @RequestParam(required = false) Integer limit,
            // With `page`: one page of any period (from/to dates), with totals -- the Audit tab.
            // Without it: the newest entries as a plain list -- the activity feed.
            @RequestParam(required = false) Integer page,
            @RequestParam(defaultValue = "25") int size,
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) java.time.LocalDate from,
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) java.time.LocalDate to) {
        try {
            Long hospitalId = securityHelper.getCurrentHospitalId();

            if (hospitalId == null) {
                log.warn("Hospital ID is null for current user - returning empty activity feed");
                return ResponseEntity.ok(java.util.Collections.emptyList());
            }

            String mappedRole = null;
            if (role != null && !role.trim().isEmpty() && !"ALL".equalsIgnoreCase(role)) {
                mappedRole = role.trim();
            }
            if (page != null) {
                return ResponseEntity.ok(AuditLogService.pageBody(
                        auditLogService.pageHospitalLogs(hospitalId, search, mappedRole, from, to, page, size)));
            }

            // The limit is applied in the query (newest first, capped), not after loading them all.
            List<AuditLog> logs = auditLogService.getLogsByHospitalId(hospitalId, search, mappedRole, limit);
            return ResponseEntity.ok(logs);
        } catch (Exception e) {
            log.warn("Could not retrieve hospital context - returning empty activity feed", e);
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

    /**
     * Get pharmacy-specific audit logs (SUPPLIER, MEDICINE_BATCH, PURCHASE_INVOICE, PHARMACY_SALE).
     * Filters by role based on pharmacy plan type (SINGLE_PHARMACIST_ADMIN, SINGLE_PHARMACY, MULTI_PHARMACY).
     */
    @GetMapping("/pharmacy")
    public ResponseEntity<?> getPharmacyLogs(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String role,
            @RequestParam(required = false) Integer page,
            @RequestParam(defaultValue = "25") int size,
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) java.time.LocalDate from,
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) java.time.LocalDate to) {
        try {
            Long hospitalId = securityHelper.getCurrentHospitalId();
            Long branchId = securityHelper.getCurrentBranchId();

            if (hospitalId == null) {
                return ResponseEntity.ok(java.util.Collections.emptyList());
            }

            String mappedRole = null;
            if (role != null && !role.trim().isEmpty() && !"ALL".equalsIgnoreCase(role)) {
                mappedRole = role.trim();
            }

            if (page != null) {
                return ResponseEntity.ok(AuditLogService.pageBody(auditLogService.pagePharmacyLogs(
                        hospitalId, branchId, mappedRole, search, from, to, page, size)));
            }
            List<AuditLog> logs = auditLogService.getPharmacyLogs(hospitalId, branchId, mappedRole, search);
            return ResponseEntity.ok(logs);
        } catch (Exception e) {
            log.warn("Could not retrieve pharmacy audit logs", e);
            return ResponseEntity.ok(java.util.Collections.emptyList());
        }
    }
}
