package com.hms.dto;

import lombok.Data;

@Data
public class BreakdownTicketRequest {
    private Long equipmentId;
    private String priority; // LOW / MEDIUM / CRITICAL
    private String remarks;
}
