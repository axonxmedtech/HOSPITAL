package com.hms.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * FluidOutputRequest - DTO for manual fluid output logs.
 *
 * @author HMS Team
 * @version Phase-1
 */
@Data
public class FluidOutputRequest {

    @NotNull(message = "Admission ID is mandatory")
    private Long admissionId;

    @NotBlank(message = "Output type is mandatory")
    private String type; // URINE / STOOL / VOMIT / DRAIN / DIALYSIS

    @NotNull(message = "Volume is mandatory")
    @Min(value = 1, message = "Volume must be greater than zero")
    private Integer volumeMl;

    private String color;

    @NotBlank(message = "Description is mandatory")
    private String description;
}
