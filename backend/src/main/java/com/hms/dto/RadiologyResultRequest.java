package com.hms.dto;

import lombok.Data;

@Data
public class RadiologyResultRequest {
    private String findings;
    private String impression;
    private Boolean isAbnormal = false;
    private String resultFileUrl;
    private String verifiedByName;
}
