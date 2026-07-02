package com.hms.dto;

import lombok.Data;

@Data
public class TrainingMasterRequest {
    private String title;
    private String category;
    private String description;
    private Boolean mandatory;
    private Integer validityPeriod;
    private String targetRoles;
}
