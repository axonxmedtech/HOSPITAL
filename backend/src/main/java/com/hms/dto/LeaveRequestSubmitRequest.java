package com.hms.dto;

import lombok.Data;

import java.time.LocalDate;

@Data
public class LeaveRequestSubmitRequest {
    private String leaveType; // CASUAL / SICK / EARNED / MATERNITY
    private LocalDate startDate;
    private LocalDate endDate;
}
