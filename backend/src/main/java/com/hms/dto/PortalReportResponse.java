package com.hms.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PortalReportResponse {
    private Long orderId;
    private String category; // LAB / RADIOLOGY
    private String testName;
    private LocalDateTime releasedAt;
    private String summary;   // resultSummary for lab, impression for radiology
    private String parameters; // JSON parameters (lab) or findings (radiology)
    private Boolean isAbnormal;
    private String resultFileUrl;
}
