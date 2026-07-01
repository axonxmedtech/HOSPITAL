package com.hms.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * ConsentSignRequest - DTO for capturing a digital signature slot.
 *
 * @author HMS Team
 * @version Phase-1
 */
@Data
public class ConsentSignRequest {

    private String signatureType; // e.g. "WET", "DIGITAL", "THUMBPRINT"
    private Boolean patientSigned;
    private Boolean guardianSigned;
    private String relationship;
}
