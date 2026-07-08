package com.hms.dto;

import lombok.Data;
import java.time.LocalDate;

/** Doctor's "Create Surgery Request" payload (from the IPD case view). */
@Data
public class CreateSurgeryRequest {
    private Long ipdAdmissionId; // required
    private String procedureName;
    private String clinicalNotes;
    private String priority;      // ELECTIVE | EMERGENCY
    private LocalDate preferredDate;
}
