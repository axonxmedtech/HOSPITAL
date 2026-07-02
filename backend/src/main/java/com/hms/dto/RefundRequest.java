package com.hms.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class RefundRequest {
    private Long billingId;
    private BigDecimal amount;
    private String reason;
    // used only by the approve/reject actions
    private String rejectionReason;
}
