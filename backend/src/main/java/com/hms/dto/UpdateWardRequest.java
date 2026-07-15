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

    /**
     * New bed count for the ward. Growing appends fresh available beds; shrinking removes
     * only free beds. Null leaves the bed list untouched (partial update).
     */
    @jakarta.validation.constraints.Min(value = 0, message = "Total beds cannot be negative")
    private Integer totalBeds;
}
