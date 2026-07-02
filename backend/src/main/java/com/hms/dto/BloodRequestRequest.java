package com.hms.dto;

import lombok.Data;

@Data
public class BloodRequestRequest {
    private Long patientId;
    private Long admissionId;
    private String department;
    private String component;
    private Integer unitsRequested;
    private String priority;
}
