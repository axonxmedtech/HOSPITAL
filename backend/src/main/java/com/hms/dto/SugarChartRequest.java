package com.hms.dto;

import lombok.Data;

/**
 * Create/update payload for a sugar-chart entry.
 */
@Data
public class SugarChartRequest {
    private Long ipdAdmissionId; // required on create; ignored on update
    private String bloodSugar;
    private String treatment;
}
