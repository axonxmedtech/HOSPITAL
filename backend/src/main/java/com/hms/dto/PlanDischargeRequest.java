package com.hms.dto;

import java.time.LocalDate;

public class PlanDischargeRequest {
    private String finalDiagnosis;
    private String treatmentGiven;
    private String dischargeNotes;
    private LocalDate followUpDate;
    /** Time for the follow-up appointment; 10:00 when not given. */
    private java.time.LocalTime followUpTime;
    /** IMPROVED / STABLE / UNCHANGED / DETERIORATED. */
    private String conditionAtDischarge;
    private String proceduresDone;
    private String investigations;
    private String advice;
    private java.util.List<DischargeMedication> dischargeMedications;

    /** One medicine to take at home. */
    public static class DischargeMedication {
        private String name;
        private String dose;
        private String frequency;
        private Integer durationDays;
        private String instructions;

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getDose() { return dose; }
        public void setDose(String dose) { this.dose = dose; }
        public String getFrequency() { return frequency; }
        public void setFrequency(String frequency) { this.frequency = frequency; }
        public Integer getDurationDays() { return durationDays; }
        public void setDurationDays(Integer durationDays) { this.durationDays = durationDays; }
        public String getInstructions() { return instructions; }
        public void setInstructions(String instructions) { this.instructions = instructions; }
    }

    public java.time.LocalTime getFollowUpTime() { return followUpTime; }
    public void setFollowUpTime(java.time.LocalTime followUpTime) { this.followUpTime = followUpTime; }
    public String getConditionAtDischarge() { return conditionAtDischarge; }
    public void setConditionAtDischarge(String conditionAtDischarge) { this.conditionAtDischarge = conditionAtDischarge; }
    public String getProceduresDone() { return proceduresDone; }
    public void setProceduresDone(String proceduresDone) { this.proceduresDone = proceduresDone; }
    public String getInvestigations() { return investigations; }
    public void setInvestigations(String investigations) { this.investigations = investigations; }
    public String getAdvice() { return advice; }
    public void setAdvice(String advice) { this.advice = advice; }
    public java.util.List<DischargeMedication> getDischargeMedications() { return dischargeMedications; }
    public void setDischargeMedications(java.util.List<DischargeMedication> dischargeMedications) { this.dischargeMedications = dischargeMedications; }
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
