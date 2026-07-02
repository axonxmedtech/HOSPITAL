package com.hms.dto;

import lombok.Data;

import java.time.LocalDate;

@Data
public class CalibrationRequest {
    private Long equipmentId;
    private LocalDate calibrationDate;
    private LocalDate dueDate;
    private String agency;
    private String certificateReference;
    private String result; // PASS / FAIL
}
