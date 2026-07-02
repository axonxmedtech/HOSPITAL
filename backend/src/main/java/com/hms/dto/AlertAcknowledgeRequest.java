package com.hms.dto;

import lombok.Data;

@Data
public class AlertAcknowledgeRequest {
    private Long alertId;
    private String status; // ACKNOWLEDGED / RESOLVED
    private String remarks;
}
