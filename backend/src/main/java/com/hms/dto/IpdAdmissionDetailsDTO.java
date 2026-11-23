package com.hms.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public class IpdAdmissionDetailsDTO {
    private String ipdNumber;
    private String status;
    /** NORMAL / LAMA / REFERRED / EXPIRED / ABSCONDED — planned or final. */
    private String dischargeType;
    private String dischargeNote;
    private String cancelReason;
    private java.time.LocalDateTime cancelledAt;
    private String cancelledBy;
    private LocalDateTime dischargeDateTime;
    private PatientDTO patient;
    private AdmissionDTO admission;
    private List<MedicalRecordDTO> medicalRecords;
    private List<PrescriptionDTO> activePrescriptions;
    private List<PrescriptionDTO> allPrescriptions;
    private BillingDTO billing;
    private DischargeSummaryDTO dischargeSummary;

    /** The discharge summary as written so far (planned or final), for editing and display. */
    public static class DischargeSummaryDTO {
        public String finalDiagnosis;
        public String treatmentGiven;
        public String dischargeNotes;
        public String conditionAtDischarge;
        public String proceduresDone;
        public String investigations;
        public String advice;
        public java.util.List<com.hms.dto.PlanDischargeRequest.DischargeMedication> dischargeMedications;
        public java.time.LocalDate followUpDate;
        public java.time.LocalTime followUpTime;
        /** Set once the discharge is confirmed and the follow-up visit was booked. */
        public String followUpAppointmentId;
        public LocalDateTime updatedAt;
    }

    public static class PatientDTO {
        public Long id;
        public String name;
        public Integer age;
        public String gender;
    }

    public static class AdmissionDTO {
        public LocalDateTime admissionDateTime;
        public String admissionType;
        public String ward;
        public String bed;
        public String doctor;
        /** The attending doctor's id, so the page can offer "Change doctor" without them. */
        public Long doctorId;
        public String primaryDiagnosis;
        /** True once the admission form is saved and the patient confirmed as admitted. */
        public boolean admissionConfirmed;
        /** CALENDAR_DAY or HOURS_24 — how bed days are counted for this stay. */
        public String bedChargeRule;
        public Integer graceHours;
        /** The package covering the stay, if any. */
        public Long packageId;
        public String packageName;
        public Integer packageDays;
    }

    public static class MedicalRecordDTO {
        public String date;
        public String doctor;
        public String diagnosis;
        public String notes;
    }

    public static class PrescriptionDTO {
        public Long id;
        public String name;
        public String type;
        public String route;
        public String frequency;
        public String status;
        public String startDate;
        public String dosage;
        public Integer durationDays;
    }

    public static class BillingDTO {
        public BigDecimal totalAmount;
        public BigDecimal paidAmount;
        public BigDecimal balance;
        /** Discount on this admission's bill(s). */
        public java.math.BigDecimal discountAmount;
        /** Every unpaid bill of the patient (OPD too) — all must be settled before discharge. */
        public java.util.List<java.util.Map<String, Object>> outstandingBills;
        /** Advance taken on this admission (included in paidAmount); a negative balance is unused advance. */
        public BigDecimal advancePaid;
        public BigDecimal packagePrice;
        public java.util.List<AdvanceDTO> advances;
        /** What an insurer has approved and is still expected to pay; the patient's share is balance minus this. */
        public BigDecimal insuranceExpected;
    }

    public static class AdvanceDTO {
        public Long id;
        public BigDecimal amount;
        public String mode;
        public String reference;
        public LocalDateTime createdAt;
    }

    public static class AdministeredItemDTO {
        public String name;
        public Integer quantity;
        public String administeredAt; // date of the medical record / billing entry
    }

    private List<AdministeredItemDTO> administeredItems;

    public String getIpdNumber() { return ipdNumber; }
    public void setIpdNumber(String ipdNumber) { this.ipdNumber = ipdNumber; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public PatientDTO getPatient() { return patient; }
    public void setPatient(PatientDTO patient) { this.patient = patient; }

    public AdmissionDTO getAdmission() { return admission; }
    public void setAdmission(AdmissionDTO admission) { this.admission = admission; }

    public List<MedicalRecordDTO> getMedicalRecords() { return medicalRecords; }
    public void setMedicalRecords(List<MedicalRecordDTO> medicalRecords) { this.medicalRecords = medicalRecords; }

    public List<PrescriptionDTO> getActivePrescriptions() { return activePrescriptions; }
    public void setActivePrescriptions(List<PrescriptionDTO> activePrescriptions) { this.activePrescriptions = activePrescriptions; }

    public List<PrescriptionDTO> getAllPrescriptions() { return allPrescriptions; }
    public void setAllPrescriptions(List<PrescriptionDTO> allPrescriptions) { this.allPrescriptions = allPrescriptions; }

    public BillingDTO getBilling() { return billing; }
    public void setBilling(BillingDTO billing) { this.billing = billing; }

    public List<AdministeredItemDTO> getAdministeredItems() { return administeredItems; }
    public void setAdministeredItems(List<AdministeredItemDTO> administeredItems) { this.administeredItems = administeredItems; }

    public String getDischargeType() { return dischargeType; }
    public void setDischargeType(String dischargeType) { this.dischargeType = dischargeType; }
    public DischargeSummaryDTO getDischargeSummary() { return dischargeSummary; }
    public void setDischargeSummary(DischargeSummaryDTO dischargeSummary) { this.dischargeSummary = dischargeSummary; }

    public String getCancelReason() { return cancelReason; }
    public void setCancelReason(String cancelReason) { this.cancelReason = cancelReason; }
    public java.time.LocalDateTime getCancelledAt() { return cancelledAt; }
    public void setCancelledAt(java.time.LocalDateTime cancelledAt) { this.cancelledAt = cancelledAt; }
    public String getCancelledBy() { return cancelledBy; }
    public void setCancelledBy(String cancelledBy) { this.cancelledBy = cancelledBy; }

    public String getDischargeNote() { return dischargeNote; }
    public void setDischargeNote(String dischargeNote) { this.dischargeNote = dischargeNote; }
    public LocalDateTime getDischargeDateTime() { return dischargeDateTime; }
    public void setDischargeDateTime(LocalDateTime dischargeDateTime) { this.dischargeDateTime = dischargeDateTime; }
}
