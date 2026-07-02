package com.hms.dto;

import lombok.Data;

@Data
public class FeedbackTokenIssueRequest {
    private Long patientId;
    private Long appointmentId;
    private Long admissionId;
    private String feedbackType; // OPD / IPD
}
