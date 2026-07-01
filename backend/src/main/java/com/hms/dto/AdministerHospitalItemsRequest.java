package com.hms.dto;

import java.util.List;
import lombok.Data;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

@Data
public class AdministerHospitalItemsRequest {
    @Valid
    private List<HospitalItem> items;

    @Data
    public static class HospitalItem {
        private Long stockId;
        private String name;

        @NotNull(message = "Quantity is required")
        @Min(value = 1, message = "Quantity must be positive")
        private Integer quantity;

        private String feeName;
        private java.math.BigDecimal feeAmount;
    }
}
