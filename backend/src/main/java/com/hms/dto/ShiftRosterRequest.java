package com.hms.dto;

import lombok.Data;

import java.time.LocalDate;

@Data
public class ShiftRosterRequest {
    private Long employeeId;
    private String department;
    private String shift; // MORNING / EVENING / NIGHT / ON_CALL
    private LocalDate date;
}
