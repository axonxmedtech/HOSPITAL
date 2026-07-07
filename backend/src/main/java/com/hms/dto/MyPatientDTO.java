package com.hms.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * A patient (IPD admission) currently assigned to the logged-in nurse.
 */
@Data
public class MyPatientDTO {
    private Long ipdAdmissionId;
    private String ipdNumber;
    private String patientName;
    private Integer age;
    private String gender;
    private String wardName;
    private String bedCode;
    private String primaryDiagnosis;
    private String doctorName;
    private LocalDateTime admissionDateTime;
    private String status;
    private Boolean admissionConfirmed;
}
