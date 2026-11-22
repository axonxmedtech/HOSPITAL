package com.hms.dto;

import lombok.Data;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import com.hms.validation.NoEmoji;

@Data
public class CreateIpdAdmissionRequest {
    private Long opdId; // source OPD id
    private Long patientId; // Direct patient id
    private Long doctorId; // Direct doctor id
    private Long wardId;
    private Long bedId;

    @Pattern(regexp = "^(EMERGENCY|ELECTIVE)?$", message = "Admission type must be EMERGENCY or ELECTIVE")
    private String admissionType; // EMERGENCY / ELECTIVE

    @Size(max = 2000, message = "Primary diagnosis is too long")
    @NoEmoji
    private String primaryDiagnosis;

    /** Optional fixed-price package to apply at admission. */
    private Long packageId;

    /** Optional advance taken at the desk (required for a planned admission when the hospital sets a minimum). */
    @jakarta.validation.constraints.PositiveOrZero(message = "Advance cannot be negative")
    private java.math.BigDecimal advanceAmount;

    /** CASH / CARD / UPI for the advance. */
    private String advanceMode;

    /** UTR / reference for a UPI or card advance. */
    @Size(max = 100, message = "Reference is too long")
    private String advanceReference;

    /** The reservation this admission uses, when the bed was held by name (not for a registered patient). */
    @Size(max = 36)
    private String reservationId;
}
