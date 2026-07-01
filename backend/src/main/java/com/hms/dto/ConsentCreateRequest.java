package com.hms.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * ConsentCreateRequest - DTO for creating a new PatientConsent record.
 *
 * @author HMS Team
 * @version Phase-1
 */
@Data
public class ConsentCreateRequest {

    @NotNull(message = "Patient ID is mandatory")
    private Long patientId;

    private Long admissionId;

    @NotBlank(message = "Encounter type is mandatory")
    private String encounterType; // e.g. "IPD", "OPD"

    @NotBlank(message = "Consent type is mandatory")
    private String consentType; // e.g. "GENERAL", "BLOOD"

    private String language;

    private Long bloodRequestId;

    private String signatureType;

    private Boolean patientSigned;

    private Boolean guardianSigned;

    private String relationship;

    private String remarks;
}
