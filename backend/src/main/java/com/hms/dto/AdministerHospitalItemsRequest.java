package com.hms.dto;

import java.util.List;
import lombok.Data;

@Data
public class AdministerHospitalItemsRequest {
    private List<HospitalItem> items;

    @Data
    public static class HospitalItem {
        private Long stockId;
        private String name;
        private Integer quantity;
        private String feeName;
        private java.math.BigDecimal feeAmount;
    }
}
