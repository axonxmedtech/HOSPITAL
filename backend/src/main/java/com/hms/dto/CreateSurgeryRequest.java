package com.hms.dto;

import lombok.Data;
import java.time.LocalDate;

/**
 * "Create Surgery Request" payload. Either ipdAdmissionId (inpatient, from the IPD
 * case view) or patientId (day-care procedure with no admission) must be supplied.
 */
@Data
public class CreateSurgeryRequest {
    private Long ipdAdmissionId;  // inpatient procedures
    private Long patientId;       // day-care procedures (no admission)
    private String procedureName;
    private String clinicalNotes;
    private String priority;      // ELECTIVE | EMERGENCY
    private LocalDate preferredDate;
}
