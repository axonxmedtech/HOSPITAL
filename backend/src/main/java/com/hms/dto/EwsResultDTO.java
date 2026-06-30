package com.hms.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class EwsResultDTO {
    private int totalScore;
    private String severity;     // "NORMAL" | "MEDIUM" | "HIGH" | "UNKNOWN"
    private String message;
    private int sbpScore;
    private int pulseScore;
    private int tempScore;
    private int spo2Score;
    private int respRateScore;
    private String bloodPressure;
    private Integer pulse;
    private Double temperature;
    private Integer spo2;
    private Integer respiratoryRate;
}
