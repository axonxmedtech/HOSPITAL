package com.hms.dto;

import lombok.Data;

@Data
public class TrainingAttendanceCorrectRequest {
    private String attendanceStatus; // PRESENT / ABSENT / LATE
    private String reason;
}
