package com.hms.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class CdssCheckRequest {
    private Long patientId;
    private Long ipdAdmissionId;
    private String medicineName;
    private Long medicineMasterId;
}
