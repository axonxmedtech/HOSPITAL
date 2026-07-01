package com.hms.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class AnaesthesiaRecordRequest {
    private String anaesthesiaType;
    private String asaGrade;
    private String airwayType;
    private String ventilationMode;
    private LocalDateTime inductionTime;
    private LocalDateTime completionTime;
    private String notes;
}
