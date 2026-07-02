package com.hms.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DoctorRoundRequest {
    private String subjective;
    private String objective;
    private String assessment;
    private String plan;
    private LocalDateTime nextRoundAt;

    // PROGRESS_NOTE (Form 13, default) or REASSESSMENT (Form 11)
    private String assessmentType;

    // Required when amending a signed note
    private String amendmentReason;

    private String clinicalStatus;
    private String clinicalImpression;
    private Long baselineAssessmentId;
    private java.util.List<com.hms.entity.DoctorOrder> spawnedOrders;
    private java.util.List<String> stoppedOrderPublicIds;
    private java.util.List<ReferralRequest> referrals;

    public DoctorRoundRequest(String subjective, String objective, String assessment, String plan,
                              LocalDateTime nextRoundAt, String assessmentType, String amendmentReason) {
        this.subjective = subjective;
        this.objective = objective;
        this.assessment = assessment;
        this.plan = plan;
        this.nextRoundAt = nextRoundAt;
        this.assessmentType = assessmentType;
        this.amendmentReason = amendmentReason;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ReferralRequest {
        private String specialty;
        private String reason;
        private String urgency;
    }
}
