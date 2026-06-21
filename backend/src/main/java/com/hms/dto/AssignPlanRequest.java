package com.hms.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class AssignPlanRequest {

    @NotBlank(message = "Hospital public ID is required")
    private String hospitalPublicId;

    @NotNull(message = "Billing period is required (MONTHLY or YEARLY)")
    private String billingPeriod; // MONTHLY | YEARLY
}
