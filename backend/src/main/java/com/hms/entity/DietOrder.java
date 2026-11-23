package com.hms.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * The diet a doctor sets for an inpatient. Changing the diet ends the current order and starts a
 * new one, so the history of what the patient was allowed to eat, and when, is kept.
 */
@Entity
@Table(name = "diet_orders")
@Data
@NoArgsConstructor
public class DietOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "public_id", nullable = false, unique = true, length = 36)
    private String publicId;

    @Column(name = "hospital_id", nullable = false)
    private Long hospitalId;

    @Column(name = "ipd_admission_id", nullable = false)
    private Long ipdAdmissionId;

    /** NORMAL, SOFT, LIQUID, CLEAR_LIQUID, DIABETIC, LOW_SALT, RENAL, HIGH_PROTEIN, NBM (nil by mouth), OTHER. */
    @Column(name = "diet_type", nullable = false, length = 30)
    private String dietType;

    @Column(length = 500)
    private String instructions;

    @Column(name = "ordered_by_doctor_id")
    private Long orderedByDoctorId;

    @Column(name = "ordered_by")
    private String orderedBy;

    @Column(name = "started_at", nullable = false)
    private LocalDateTime startedAt;

    /** Null while this is the patient's current diet. */
    @Column(name = "ended_at")
    private LocalDateTime endedAt;

    @PrePersist
    void onCreate() {
        if (publicId == null) publicId = java.util.UUID.randomUUID().toString();
    }
}
