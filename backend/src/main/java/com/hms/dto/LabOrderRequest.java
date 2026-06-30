package com.hms.dto;

import lombok.Data;

/**
 * LabOrderRequest — DTO for placing a new lab order.
 * Either ipdAdmissionId or opdId must be present to link the order
 * to a clinical encounter (but both are nullable for flexibility).
 */
@Data
public class LabOrderRequest {
    private String testName;
    private Long labTestMasterId;
    private Long patientId;
    private Long ipdAdmissionId;
    private Long opdId;
    private String notes;
    private String priority = "ROUTINE"; // ROUTINE | URGENT
}
