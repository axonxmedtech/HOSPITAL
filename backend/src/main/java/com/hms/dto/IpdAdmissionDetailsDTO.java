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
    private LocalDateTime dischargeDateTime;
    private PatientDTO patient;
    private AdmissionDTO admission;
    private List<MedicalRecordDTO> medicalRecords;
    private List<PrescriptionDTO> activePrescriptions;
    private List<PrescriptionDTO> allPrescriptions;
    private BillingDTO billing;

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
    public String getDischargeNote() { return dischargeNote; }
    public void setDischargeNote(String dischargeNote) { this.dischargeNote = dischargeNote; }
    public LocalDateTime getDischargeDateTime() { return dischargeDateTime; }
    public void setDischargeDateTime(LocalDateTime dischargeDateTime) { this.dischargeDateTime = dischargeDateTime; }
}
