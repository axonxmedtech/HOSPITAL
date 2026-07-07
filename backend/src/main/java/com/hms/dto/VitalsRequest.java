package com.hms.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Create/update payload for a vitals record. All measurements are optional
 * individually, but at least one must be present (enforced in the service).
 */
@Data
public class VitalsRequest {
    private Long ipdAdmissionId;      // required on create; ignored on update
    private LocalDateTime recordedAt; // optional; defaults to now
    private BigDecimal temperature;
    private Integer pulse;
    private Integer bpSystolic;
    private Integer bpDiastolic;
    private Integer respiratoryRate;
    private Integer spo2;
    private BigDecimal weight;
    private Integer painScore;
    private String remarks;
}
