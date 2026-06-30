package com.hms.dto;

import lombok.Data;

@Data
public class RadiologyOrderRequest {
    private String testName;
    private Long radiologyTestMasterId;
    private Long patientId;
    private Long ipdAdmissionId;
    private Long opdId;
    private String notes;
    private String priority = "ROUTINE"; // ROUTINE / URGENT
}
