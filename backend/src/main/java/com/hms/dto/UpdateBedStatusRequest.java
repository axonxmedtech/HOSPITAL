package com.hms.dto;

import lombok.Data;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

@Data
public class UpdateBedStatusRequest {
    @NotBlank(message = "status is required")
    @Pattern(regexp = "^(available|occupied|maintenance)$", message = "status must be one of: available, occupied, maintenance")
    private String status; // allowed: available, occupied, maintenance
}
