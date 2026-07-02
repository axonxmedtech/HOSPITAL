package com.hms.dto;

import lombok.Data;

@Data
public class PortalOtpRequestRequest {
    private Long hospitalId;
    private String mobile;
    private String uhid;
}
