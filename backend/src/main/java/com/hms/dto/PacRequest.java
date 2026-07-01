package com.hms.dto;

import lombok.Data;

@Data
public class PacRequest {
    private String asaClass;
    private String airwayAssessment;
    private String systemicAssessment;
    private String fitnessStatus;      // FIT / FIT_WITH_PRECAUTIONS / FURTHER_EVALUATION / DEFERRED
    private String plannedAnaesthesia;
    private String remarks;
}
