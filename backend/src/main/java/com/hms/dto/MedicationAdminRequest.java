package com.hms.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * Payload to record administration of a prescribed dose.
 */
@Data
public class MedicationAdminRequest {
    private Long ipdAdmissionId;
    private Long prescriptionId;
    private String status;              // GIVEN / SKIPPED / DELAYED / REFUSED / NOT_AVAILABLE
    private LocalDateTime scheduledTime;   // optional
    private LocalDateTime administeredTime; // required for GIVEN / DELAYED
    private String remarks;
}
