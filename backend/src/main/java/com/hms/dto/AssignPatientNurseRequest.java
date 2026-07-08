package com.hms.dto;

import lombok.Data;

@Data
public class AssignPatientNurseRequest {
    private Long ipdAdmissionId;   // required
    private Long nurseProfileId;   // required — the staff nurse to assign
}
