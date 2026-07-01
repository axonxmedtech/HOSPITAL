package com.hms.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class PacuRecordRequest {
    private String recoveryBed;
    private LocalDateTime recoveryStart;
    private LocalDateTime recoveryEnd;

    private String consciousness;
    private String orientation;
    private String airwayStatus;
    private String breathingStatus;
    private String circulationStatus;
    private String nauseaSeverity;
    private Boolean vomitingPresent;
    private Integer painScore;

    private Integer aldreteActivity;
    private Integer aldreteRespiration;
    private Integer aldreteCirculation;
    private Integer aldreteConsciousness;
    private Integer aldreteOxygen;

    private String transferDestination;
    private String handoverNotes;
}
