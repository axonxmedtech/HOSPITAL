package com.hms.dto;

import lombok.Data;

@Data
public class InstrumentCountRequest {
    private String scrubNurse;
    private String circulatingNurse;
    private String countSummary;
    private String initialCountStatus;   // PENDING / VERIFIED
    private String finalCountStatus;     // PENDING / VERIFIED / MISMATCH

    // Discrepancy resolution (BR-4)
    private Boolean searchConducted;
    private Boolean xrayPerformed;
    private String resolutionRemarks;
}
