package com.hms.dto;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalTime;

@Data
public class MarkAttendanceRequest {
    private Long nurseProfileId;   // required
    private LocalDate date;        // required
    private String status;         // required: PRESENT/ABSENT/HALF_DAY/LEAVE/HOLIDAY/LATE
    private LocalTime checkInTime; // optional
    private LocalTime checkOutTime;// optional
    private String remarks;        // optional
}
