package com.hms.dto;

import lombok.Data;

import java.time.LocalDate;

@Data
public class OtReadinessRequest {
    private String otRoom;
    private LocalDate readinessDate;
    private Boolean cleaningDone;
    private Boolean sterilityDone;
    private Boolean equipmentOk;
    private String status; // PENDING / READY
}
