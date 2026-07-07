package com.hms.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * AdmissionForm - the IPD admission form a nurse fills to complete a patient's
 * admission (Phase 1 Nurse module). One form per IPD admission. Known values
 * (patient/admission/doctor/ward/bed) are pre-filled when the draft is created;
 * the rest (relative, insurance, category, etc.) are entered by the nurse.
 */
@Entity
@Table(name = "admission_forms")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AdmissionForm {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String publicId;

    @Column(name = "hospital_id", nullable = false)
    private Long hospitalId;

    @Column(name = "ipd_admission_id", nullable = false, unique = true)
    private Long ipdAdmissionId;

    // Header
    @Column(name = "prn_no", length = 60)
    private String prnNo;
    @Column(name = "bed_no", length = 60)
    private String bedNo;
    @Column(name = "category", length = 40) // ICU / Deluxe / Private / Semi Private / General
    private String category;

    // Patient
    @Column(name = "patient_surname", length = 100)
    private String patientSurname;
    @Column(name = "patient_first_name", length = 100)
    private String patientFirstName;
    @Column(name = "husband_father_name", length = 150)
    private String husbandFatherName;
    @Column(name = "patient_address", columnDefinition = "text")
    private String patientAddress;
    @Column(name = "age", length = 20)
    private String age;
    @Column(name = "sex", length = 20)
    private String sex;
    @Column(name = "occupation", length = 100)
    private String occupation;

    // Category / payer
    @Column(name = "patient_category", length = 40) // HOSPITAL / PRIVATE / PMC_PMT
    private String patientCategory;
    @Column(name = "mediclaim", length = 20) // CASHLESS / REIMBURSE
    private String mediclaim;
    @Column(name = "tpa_name", length = 200)
    private String tpaName;

    // Contacts
    @Column(name = "relative_name", length = 150)
    private String relativeName;
    @Column(name = "email", length = 120)
    private String email;
    @Column(name = "telephone", length = 40)
    private String telephone;
    @Column(name = "receptionist_name", length = 120)
    private String receptionistName;
    @Column(name = "ref_dr", length = 150)
    private String refDr;

    // Admission clinical
    @Column(name = "ipd_registration_no", length = 60)
    private String ipdRegistrationNo;
    @Column(name = "department", length = 120)
    private String department;
    @Column(name = "under_care_of_dr", length = 150)
    private String underCareOfDr;
    @Column(name = "admitted_date", length = 40)
    private String admittedDate;
    @Column(name = "admitted_time", length = 40)
    private String admittedTime;
    @Column(name = "prov_diagnosis1", columnDefinition = "text")
    private String provDiagnosis1;
    @Column(name = "prov_diagnosis2", columnDefinition = "text")
    private String provDiagnosis2;

    @Column(name = "hypersensitivity_history", columnDefinition = "text")
    private String hypersensitivityHistory;

    // Relatives block (for signature)
    @Column(name = "relative_address", columnDefinition = "text")
    private String relativeAddress;
    @Column(name = "relative_phone", length = 40)
    private String relativePhone;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    public void prePersist() {
        if (this.publicId == null) {
            this.publicId = java.util.UUID.randomUUID().toString();
        }
    }
}
