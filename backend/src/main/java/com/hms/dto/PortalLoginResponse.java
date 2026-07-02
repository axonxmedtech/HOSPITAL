package com.hms.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PortalLoginResponse {
    private String token;
    private Long patientId;
    private String patientName;
    private String uhid;
}
