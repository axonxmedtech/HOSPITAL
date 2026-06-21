package com.hms.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class CreatePlanRequest {

    @NotBlank(message = "Plan name is required")
    private String name;

    @NotNull(message = "Plan type is required")
    private String type; // HOSPITAL | CLINIC | PHARMACY

    @NotNull(message = "Monthly price is required")
    private BigDecimal monthlyPrice;

    @NotNull(message = "Yearly price is required")
    private BigDecimal yearlyPrice;

    private List<String> modules;

    private List<String> features;

    private Boolean inClinic = false;
}
