package com.hms.dto;

import lombok.Data;

@Data
public class PostopOrdersRequest {
    private String postopDiagnosis;
    private String condition;
    private String dietOrder;
    private String activityOrder;
    private String medications;
    private String monitoringPlan;
    private String investigations;
    private String escalationInstructions;
}
