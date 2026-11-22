package com.hms.service.pharmacy;

import com.hms.security.SecurityContextHelper;
import com.hms.service.AuditLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Pharmacy actions that change money or stock outside a normal sale — refunds, stock
 * adjustments, blocking or disposing a batch, supplier returns, posting a purchase — written to
 * the audit log with who did it and in which branch. They show in the pharmacy's Audit Logs
 * screen (entity types MEDICINE_BATCH, PURCHASE_INVOICE, PHARMACY_SALE). Sales themselves are not
 * repeated here: every sale is already its own numbered record.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PharmacyAuditTrail {

    private final AuditLogService auditLogService;
    private final SecurityContextHelper securityHelper;

    public void write(String action, String entityType, String entityId, String details) {
        write(action, entityType, entityId, details, null);
    }

    public void write(String action, String entityType, String entityId, String details, String reason) {
        try {
            auditLogService.logAction(action, details, securityHelper.getCurrentUserEmail(),
                    securityHelper.getCurrentHospitalId(), securityHelper.getCurrentBranchId(),
                    entityType, entityId, reason);
        } catch (RuntimeException e) {
            log.warn("Pharmacy audit entry not written ({}): {}", action, e.getMessage());
        }
    }
}
