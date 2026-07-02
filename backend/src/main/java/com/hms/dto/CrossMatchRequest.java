package com.hms.dto;

import lombok.Data;

@Data
public class CrossMatchRequest {
    private Long requestId;
    private Long bloodUnitId;
    private String result; // COMPATIBLE / INCOMPATIBLE
}
