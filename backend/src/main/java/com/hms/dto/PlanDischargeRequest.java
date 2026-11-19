package com.hms.dto;

import java.time.LocalDate;

public class PlanDischargeRequest {
    private String finalDiagnosis;
    private String treatmentGiven;
    private String dischargeNotes;
    private LocalDate followUpDate;
    /** NORMAL (default), LAMA, REFERRED, EXPIRED or ABSCONDED. */
    private String dischargeType;
    /** Referral hospital, cause / time of death, etc. */
    private String dischargeTypeNote;

    public String getDischargeType() { return dischargeType; }
    public void setDischargeType(String dischargeType) { this.dischargeType = dischargeType; }

    public String getDischargeTypeNote() { return dischargeTypeNote; }
    public void setDischargeTypeNote(String dischargeTypeNote) { this.dischargeTypeNote = dischargeTypeNote; }

    public String getFinalDiagnosis() { return finalDiagnosis; }
    public void setFinalDiagnosis(String finalDiagnosis) { this.finalDiagnosis = finalDiagnosis; }

    public String getTreatmentGiven() { return treatmentGiven; }
    public void setTreatmentGiven(String treatmentGiven) { this.treatmentGiven = treatmentGiven; }

    public String getDischargeNotes() { return dischargeNotes; }
    public void setDischargeNotes(String dischargeNotes) { this.dischargeNotes = dischargeNotes; }

    public LocalDate getFollowUpDate() { return followUpDate; }
    public void setFollowUpDate(LocalDate followUpDate) { this.followUpDate = followUpDate; }
}
