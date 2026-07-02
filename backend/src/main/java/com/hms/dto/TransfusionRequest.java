package com.hms.dto;

import lombok.Data;

@Data
public class TransfusionRequest {
    private Long bloodUnitId;
    private Long patientId;
}
