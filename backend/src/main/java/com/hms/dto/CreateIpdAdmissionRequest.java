package com.hms.dto;

import lombok.Data;

@Data
public class CreateIpdAdmissionRequest {
    private Long opdId; // source OPD id
    private Long wardId;
    private Long bedId;
    private String admissionType; // EMERGENCY / ELECTIVE
    private String primaryDiagnosis;
}
