package com.hms.dto;

import lombok.Data;

@Data
public class RadiologyResultRequest {
    private String findings;
    private String impression;
    private Boolean isAbnormal = false;
    private String resultFileUrl;
    // BR-5: radiologist flags a critical finding, triggering an immediate alert.
    private Boolean isCritical = false;
}
