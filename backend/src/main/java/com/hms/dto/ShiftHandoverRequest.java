package com.hms.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * ShiftHandoverRequest - DTO payload for shift handover details.
 *
 * @author HMS Team
 * @version Phase-1
 */
@Data
public class ShiftHandoverRequest {

    @NotNull(message = "Admission ID is mandatory")
    private Long admissionId;

    @NotBlank(message = "Shift is mandatory")
    private String shift;

    @NotNull(message = "Incoming Nurse ID is mandatory")
    private Long incomingNurseId;

    private String pendingTasks;

    private String criticalAlerts;

    private String medsDue;

    private String investigationsPending;

    private Boolean doctorReviewPending;
}
