package com.hms.dto;

import lombok.Data;

@Data
public class ExecutiveAlertRequest {
    private String severity; // INFO / WARNING / CRITICAL
    private String title;
    private String description;
}
