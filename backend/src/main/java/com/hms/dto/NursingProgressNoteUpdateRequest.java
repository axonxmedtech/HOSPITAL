package com.hms.dto;

import lombok.Data;

/**
 * NursingProgressNoteUpdateRequest - DTO payload to update progress notes.
 *
 * @author HMS Team
 * @version Phase-1
 */
@Data
public class NursingProgressNoteUpdateRequest {
    private String generalCondition;
    private Integer painScore;
    private String remarks;
    private Boolean doctorNotified;
    private String doctorName;
    private String doctorAdvice;
    private String patientResponse;
    private String status; // e.g. DRAFT or SUBMITTED
}
