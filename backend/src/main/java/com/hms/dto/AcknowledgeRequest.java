package com.hms.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;

@Data
@NoArgsConstructor
public class AcknowledgeRequest {
    private Long patientId;
    private Long ipdAdmissionId;
    private List<CdssAlertDTO> alerts;
    private String overrideReason;
}
