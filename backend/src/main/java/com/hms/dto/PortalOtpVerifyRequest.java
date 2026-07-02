package com.hms.dto;

import lombok.Data;

@Data
public class PortalOtpVerifyRequest {
    private Long hospitalId;
    private String mobile;
    private String otp;
}
