package com.hms.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "discharge_summary")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DischargeSummary {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "ipd_admission_id", nullable = false, unique = true)
    private Long ipdAdmissionId;

    @Column(name = "hospital_id")
    private Long hospitalId;

    @Column(name = "patient_id")
    private Long patientId;

    @Column(name = "doctor_id")
    private Long doctorId;

    @Column(name = "final_diagnosis", columnDefinition = "text")
    private String finalDiagnosis;

    @Column(name = "treatment_given", columnDefinition = "text")
    private String treatmentGiven;

    @Column(name = "discharge_notes", columnDefinition = "text")
    private String dischargeNotes;

    @Column(name = "follow_up_date")
    private LocalDate followUpDate;

    // --- NABH discharge fields (Phase 3, all nullable/additive) ---
    @Column(name = "discharge_type", length = 50)
    private String dischargeType;         // REGULAR, LAMA, ABSCONDED, DEATH, TRANSFER

    @Column(name = "discharge_condition", length = 50)
    private String dischargeCondition;    // RECOVERED, IMPROVED, NOT_IMPROVED, CRITICAL, EXPIRED

    @Column(name = "icd_code", length = 20)
    private String icdCode;

    @Column(name = "follow_up_advice", columnDefinition = "text")
    private String followUpAdvice;

    @Column(name = "home_medications", columnDefinition = "text")
    private String homeMedications;

    @Column(name = "diet_advice", columnDefinition = "text")
    private String dietAdvice;

    @Column(name = "activity_restrictions", columnDefinition = "text")
    private String activityRestrictions;

    @Column(name = "referred_to", length = 255)
    private String referredTo;

    @Column(name = "status", length = 30)
    private String status;                // DRAFT (default) or FINALIZED

    @Column(name = "finalized_by")
    private Long finalizedBy;

    @Column(name = "finalized_at")
    private LocalDateTime finalizedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
