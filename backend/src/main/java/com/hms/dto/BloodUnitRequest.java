package com.hms.dto;

import lombok.Data;

import java.time.LocalDate;

@Data
public class BloodUnitRequest {
    private Long donorId;
    private String componentType;
    private String bloodGroup;
    private String rhType;
    private String hivResult;
    private String hbsagResult;
    private String malariaResult;
    private LocalDate expiryDate;
}
