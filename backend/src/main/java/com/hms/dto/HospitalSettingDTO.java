package com.hms.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * HospitalSettingDTO - Data Transfer Object for hospital operational settings
 *
 * Used to exchange settings (receptionMode, billingHandler) between backend and client.
 * Field values are constrained to known domain values to prevent DB constraint violations
 * and invalid operational states.
 *
 * @author HMS Team
 * @version Phase-1
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class HospitalSettingDTO {

    /**
     * Reception mode for the hospital.
     * Must be one of: HAS_RECEPTIONIST, SOLO
     */
    @NotBlank(message = "receptionMode is required")
    @Pattern(
        regexp = "HAS_RECEPTIONIST|SOLO",
        message = "receptionMode must be HAS_RECEPTIONIST or SOLO"
    )
    private String receptionMode;

    /**
     * Who handles billing in this hospital.
     * Must be one of: RECEPTIONIST, DOCTOR, BOTH
     */
    @NotBlank(message = "billingHandler is required")
    @Pattern(
        regexp = "RECEPTIONIST|DOCTOR|BOTH",
        message = "billingHandler must be RECEPTIONIST, DOCTOR, or BOTH"
    )
    private String billingHandler;

    private Boolean inClinic = true;

    @Pattern(regexp = "FIXED|MANUAL", message = "shiftMode must be FIXED or MANUAL")
    private String shiftMode = "FIXED";

    public HospitalSettingDTO(String receptionMode, String billingHandler) {
        this.receptionMode = receptionMode;
        this.billingHandler = billingHandler;
        this.inClinic = true;
    }
}
