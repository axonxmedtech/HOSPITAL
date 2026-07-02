package com.hms.dto;

import lombok.Data;

@Data
public class PortalOtpVerifyRequest {
    private String mobile;
    private String otp;
}
