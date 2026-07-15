package com.hms.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * InitialAssessment - the nurse-captured clinical fields of the NABH "Admission
 * History & Initial Assessment" form (Phase 1 Nurse module). These fields are
 * not collected in the admission or consent forms, so they are entered here.
 * One per IPD admission. Medicines come live from prescriptions (not stored).
 */
@Entity
@Table(name = "initial_assessments")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class InitialAssessment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String publicId;

    @Column(name = "hospital_id", nullable = false)
    private Long hospitalId;

    @Column(name = "ipd_admission_id", nullable = false, unique = true)
    private Long ipdAdmissionId;

    @Column(name = "chief_complaints", columnDefinition = "text")
    private String chiefComplaints;
    @Column(name = "associated_illness", columnDefinition = "text")
    private String associatedIllness;
    @Column(name = "relevant_investigations", columnDefinition = "text")
    private String relevantInvestigations;
    @Column(name = "allergies", columnDefinition = "text")
    private String allergies;
    @Column(name = "vaccination_history", columnDefinition = "text")
    private String vaccinationHistory;
    @Column(name = "others", columnDefinition = "text")
    private String others;
    @Column(name = "past_history", columnDefinition = "text")
    private String pastHistory;
    @Column(name = "family_history", columnDefinition = "text")
    private String familyHistory;
    @Column(name = "personal_history", columnDefinition = "text")
    private String personalHistory;
    @Column(name = "provisional_diagnosis", columnDefinition = "text")
    private String provisionalDiagnosis;
    @Column(name = "care_plan", columnDefinition = "text")
    private String carePlan;

    /** Selected pain-assessment score 0–10 (null if not assessed). */
    @Column(name = "pain_score")
    private Integer painScore;

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
