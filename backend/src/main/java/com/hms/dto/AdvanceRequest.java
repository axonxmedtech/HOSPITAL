package com.hms.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class AdvanceRequest {
    private Long ipdAdmissionId;
    private BigDecimal amount;
    private String paymentMode;
}
