package com.hms.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * NursingProgressNoteCreateRequest - DTO payload for creating a shift progress note.
 *
 * @author HMS Team
 * @version Phase-1
 */
@Data
public class NursingProgressNoteCreateRequest {

    @NotNull(message = "Admission ID is mandatory")
    private Long admissionId;

    @NotBlank(message = "Shift is mandatory")
    private String shift; // MORNING / EVENING / NIGHT

    @NotBlank(message = "General condition is mandatory")
    private String generalCondition;

    @NotNull(message = "Pain score is mandatory")
    @Min(value = 0, message = "Pain score cannot be less than 0")
    @Max(value = 10, message = "Pain score cannot exceed 10")
    private Integer painScore;

    private String remarks;

    private Boolean doctorNotified;

    private String doctorName;

    private String doctorAdvice;

    @NotBlank(message = "Patient response is mandatory")
    private String patientResponse;
}
