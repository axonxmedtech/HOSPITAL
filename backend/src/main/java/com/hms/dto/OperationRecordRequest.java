package com.hms.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class OperationRecordRequest {
    private String procedureName;
    private String actualProcedure;
    private String operativeFindings;
    private String estimatedBloodLoss;
    private String complicationsSummary;
    private String postOpPlan;
    private String specimens;
    private String implants;
    private LocalDateTime operationStart;
    private LocalDateTime operationEnd;
}
