package com.hms.dto;

import lombok.Data;

/**
 * ClinicalAssessmentUpdateRequest - DTO for updating a draft clinical assessment.
 *
 * @author HMS Team
 * @version Phase-1
 */
@Data
public class ClinicalAssessmentUpdateRequest {
    private String chiefComplaint;
    private String historyPresentIllness;
    private String provisionalDiagnosis;
    private String treatmentPlan;
}
