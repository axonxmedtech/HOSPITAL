package com.hms.dto;

import lombok.Data;

@Data
public class PatientFeedbackSubmitRequest {
    private String submittedBy;
    private String source;
    private Integer overallRating;
    private Integer receptionRating;
    private Integer doctorRating;
    private Integer nurseRating;
    private Integer housekeepingRating;
    private Integer billingRating;
    private String facilityRating;
    private Integer recommendScore;
    private String complaints;
    private String suggestions;
}
