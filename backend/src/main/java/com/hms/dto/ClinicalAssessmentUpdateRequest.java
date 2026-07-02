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
    private String systemicExamCvs;
    private String systemicExamRs;
    private String systemicExamCns;
    private String systemicExamGi;
    private String systemicExamMsk;
    private String systemicExamSkin;
    private String nutritionalScreening;
    private String functionalScreening;
    private String painScreening;
}
