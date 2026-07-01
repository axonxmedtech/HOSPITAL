package com.hms.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * RiskAssessmentCreateRequest - DTO payload for submitting a vulnerability risk assessment.
 *
 * @author HMS Team
 * @version Phase-1
 */
@Data
public class RiskAssessmentCreateRequest {

    @NotNull(message = "Patient ID is mandatory")
    private Long patientId;

    @NotNull(message = "Admission ID is mandatory")
    private Long admissionId;

    @NotBlank(message = "Checklist inputs JSON is mandatory")
    private String inputsJson;

    private String remarks;
}
