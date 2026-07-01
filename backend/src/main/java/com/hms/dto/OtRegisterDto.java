package com.hms.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class OtRegisterDto {
    private Long bookingId;
    private String ipdNumber;
    private String patientName;
    private String patientCustomId; // UHID
    private Integer patientAge;
    private String patientGender;
    private String procedureName;
    private LocalDateTime scheduledDateTime;
    private String surgeonName;
    private String anesthetistName;
    private String otRoomNumber;
    private String status; // booking status: SCHEDULED, IN_PROGRESS, COMPLETED, CANCELLED

    // Clinical stage statuses
    private String operationStatus;    // DRAFT / FINALIZED / NONE
    private String anaesthesiaStatus;  // ACTIVE / COMPLETED / NONE
    private String pacuStatus;         // ACTIVE / READY / TRANSFERRED / NONE
    private String checklistStatus;    // SIGN_IN / TIME_OUT / SIGN_OUT / NONE
}
