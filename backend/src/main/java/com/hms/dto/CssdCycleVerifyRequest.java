package com.hms.dto;

import lombok.Data;

@Data
public class CssdCycleVerifyRequest {
    private String chemicalResult;   // PASS / FAIL
    private String biologicalResult; // PASS / FAIL
    private String approvedBySig;
}
