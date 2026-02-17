package com.hms.dto;

import lombok.Data;
import java.math.BigDecimal;
import jakarta.validation.constraints.NotBlank;

@Data
public class UpdateWardRequest {
    // fields optional for partial update; when provided, validate basic constraints
    private String wardName;
    private BigDecimal bedPrice;
    private Integer floorNumber;
}
