package com.hms.dto;

import lombok.Data;

import java.time.LocalDate;

@Data
public class PlanDischargeRequest {
    private String finalDiagnosis;
    private String treatmentGiven;
    private String dischargeNotes;
    private LocalDate followUpDate;

    // --- NABH discharge fields (Phase 3) ---
    private String dischargeType;         // REGULAR, LAMA, ABSCONDED, DEATH, TRANSFER
    private String dischargeCondition;    // RECOVERED, IMPROVED, NOT_IMPROVED, CRITICAL, EXPIRED
    private String icdCode;
    private String followUpAdvice;
    private String homeMedications;
    private String dietAdvice;
    private String activityRestrictions;
    private String referredTo;
    private String status;                // DRAFT (default) or FINALIZED
}
