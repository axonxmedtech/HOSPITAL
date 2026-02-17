package com.hms.dto;

import lombok.Data;
import java.math.BigDecimal;

@Data
public class WardResponse {
    private Long wardId;
    private String wardName;
    private BigDecimal bedPrice;
    private Integer totalBeds;
    private Integer floorNumber;
}
