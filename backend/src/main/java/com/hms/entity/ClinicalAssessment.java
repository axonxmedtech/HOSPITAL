package com.hms.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

/**
 * ClinicalAssessment - Admission-scoped Initial Assessment snapshot entity.
 * Represents provisional diagnosis, history of present illness (HPI), chief complaint,
 * and treatment plans.
 *
 * @author HMS Team
 * @version Phase-1
 */
@Entity
@Table(name = "clinical_assessment")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ClinicalAssessment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "public_id", nullable = false, unique = true, length = 36)
    private String publicId;

    @Column(name = "hospital_id", nullable = false)
    private Long hospitalId;

    @Column(name = "patient_id", nullable = false)
    private Long patientId;

    @Column(name = "admission_id", nullable = false)
    private Long admissionId;

    @Column(name = "doctor_id", nullable = false)
    private Long doctorId;

    @Column(name = "chief_complaint", nullable = false, columnDefinition = "text")
    private String chiefComplaint;

    @Column(name = "history_present_illness", nullable = false, columnDefinition = "text")
    private String historyPresentIllness;

    @Column(name = "provisional_diagnosis", nullable = false, columnDefinition = "text")
    private String provisionalDiagnosis;

    @Column(name = "treatment_plan", nullable = false, columnDefinition = "text")
    private String treatmentPlan;

    @Column(name = "systemic_exam_cvs", columnDefinition = "text")
    private String systemicExamCvs;

    @Column(name = "systemic_exam_rs", columnDefinition = "text")
    private String systemicExamRs;

    @Column(name = "systemic_exam_cns", columnDefinition = "text")
    private String systemicExamCns;

    @Column(name = "systemic_exam_gi", columnDefinition = "text")
    private String systemicExamGi;

    @Column(name = "systemic_exam_msk", columnDefinition = "text")
    private String systemicExamMsk;

    @Column(name = "systemic_exam_skin", columnDefinition = "text")
    private String systemicExamSkin;

    @Column(name = "nutritional_screening", columnDefinition = "text")
    private String nutritionalScreening;

    @Column(name = "functional_screening", columnDefinition = "text")
    private String functionalScreening;

    @Column(name = "pain_screening", columnDefinition = "text")
    private String painScreening;

    @Column(name = "status", nullable = false, length = 20)
    private String status = "DRAFT"; // e.g. "DRAFT", "FINALIZED", "AMENDED", "ARCHIVED"

    @Column(name = "version", nullable = false)
    private Integer version = 1;

    @Column(name = "parent_id")
    private Long parentId;

    @Column(name = "finalized_by")
    private Long finalizedBy;

    @Column(name = "finalized_at")
    private LocalDateTime finalizedAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
        if (this.publicId == null) {
            this.publicId = java.util.UUID.randomUUID().toString();
        }
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
