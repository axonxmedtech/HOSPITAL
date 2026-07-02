package com.hms.dto;

import lombok.Data;

@Data
public class LeaveApprovalRequest {
    private String status; // APPROVED / REJECTED
}
