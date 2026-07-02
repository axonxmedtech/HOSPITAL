package com.hms.dto;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalTime;

@Data
public class TrainingSessionRequest {
    private Long trainingMasterId;
    private Long trainerId;
    private LocalDate sessionDate;
    private LocalTime startTime;
    private LocalTime endTime;
    private String venue;
}
