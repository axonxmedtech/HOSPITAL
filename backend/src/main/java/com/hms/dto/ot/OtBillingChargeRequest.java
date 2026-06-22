package com.hms.dto.ot;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class OtBillingChargeRequest {
    @NotBlank
    private String chargeType;

    @NotNull
    @DecimalMin("0.00")
    private BigDecimal amount;

    private String notes;
}
