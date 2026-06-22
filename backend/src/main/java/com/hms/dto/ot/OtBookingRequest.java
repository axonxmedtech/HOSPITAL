package com.hms.dto.ot;

import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
public class OtBookingRequest {
    @NotNull
    private Long patientId;
    private String patientUhid;
    private Long ipdAdmissionId;
    private String ipdNumber;
    private Long surgeonId;
    private Long assistantSurgeonId;
    private Long otRoomId;
    private String otTable;
    private String specialty;
    @NotBlank
    private String procedureName;
    private String diagnosis;
    private Integer expectedDurationMinutes;
    private String priority;
    private String surgeryType;
    @FutureOrPresent
    @NotNull
    private LocalDateTime scheduledStart;
    @NotNull
    private LocalDateTime scheduledEnd;
    private String remarks;
}
