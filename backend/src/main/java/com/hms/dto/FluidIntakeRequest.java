package com.hms.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * FluidIntakeRequest - DTO for manual fluid intake logs.
 *
 * @author HMS Team
 * @version Phase-1
 */
@Data
public class FluidIntakeRequest {

    @NotNull(message = "Admission ID is mandatory")
    private Long admissionId;

    @NotBlank(message = "Intake type is mandatory")
    private String type; // ORAL / TUBE etc.

    @NotNull(message = "Volume is mandatory")
    @Min(value = 1, message = "Volume must be greater than zero")
    private Integer volumeMl;

    @NotBlank(message = "Description is mandatory")
    private String description;
}
