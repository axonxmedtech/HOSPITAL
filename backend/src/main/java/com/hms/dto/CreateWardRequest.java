package com.hms.dto;

import lombok.Data;
import java.math.BigDecimal;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@Data
public class CreateWardRequest {
    @NotBlank(message = "wardName is required")
    private String wardName;

    @NotNull(message = "bedPrice is required")
    private BigDecimal bedPrice;

    @NotNull(message = "totalBeds is required")
    @Min(value = 0, message = "totalBeds must be >= 0")
    private Integer totalBeds;

    private Integer floorNumber;
}
