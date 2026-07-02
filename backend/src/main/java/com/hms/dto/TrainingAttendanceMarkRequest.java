package com.hms.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class TrainingAttendanceMarkRequest {
    private Long sessionId;
    private Long employeeId;
    private String department;
    private String attendanceStatus; // PRESENT / ABSENT / LATE
    private LocalDateTime checkInTime;
    private LocalDateTime checkOutTime;
    private String remarks;
}
