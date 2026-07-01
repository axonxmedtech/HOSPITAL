package com.hms.dto;

import lombok.Data;

@Data
public class ClinicalHandoverRequest {
    private String fromDepartment;
    private String toDepartment;
    private String transportMode;
    private String transportStaff;
    private String devices;
    private String monitoringPlan;
    private String pendingTasks;
    private String remarks;
}
