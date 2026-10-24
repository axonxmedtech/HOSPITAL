package com.hms.dto.icu;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class IcuWardResponse {
    private Long id;
    private String publicId;
    private Long wardId;
    private String wardName;
    private String unitType;
    private String unitTypeLabel;
    private BigDecimal bedPrice;
    private Integer totalBeds;
    private Integer floorNumber;
    private Long inchargeNurseId;
    private String inchargeNurseName;
    private Boolean staffed;
    private Integer availableBeds;
    private Integer occupiedBeds;
}
