package com.hms.dto;

import lombok.Data;

/**
 * RiskAssessmentReviewRequest - Payload for a doctor to review a risk assessment.
 *
 * @author HMS Team
 * @version Phase-1
 */
@Data
public class RiskAssessmentReviewRequest {
    private String reviewRemarks;
}
