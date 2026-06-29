package com.hms.dto;

import lombok.Data;

/**
 * LabResultRequest — DTO for entering lab test results.
 * parameters is a JSON string: [{name, value, unit, referenceRange, flag}, ...]
 */
@Data
public class LabResultRequest {
    /** JSON array of parameter objects */
    private String parameters;
    private String resultSummary;
    private Boolean isAbnormal = false;
    private String verifiedByName;
}
