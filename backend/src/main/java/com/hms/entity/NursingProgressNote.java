package com.hms.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

/**
 * NursingProgressNote - Entity representing per-shift clinical progress observations recorded by nurses.
 *
 * @author HMS Team
 * @version Phase-1
 */
@Entity
@Table(name = "nursing_progress_note")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class NursingProgressNote {

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

    @Column(name = "shift", nullable = false, length = 20)
    private String shift; // MORNING / EVENING / NIGHT

    @Column(name = "nurse_id", nullable = false)
    private Long nurseId;

    @Column(name = "general_condition", nullable = false, length = 50)
    private String generalCondition;

    @Column(name = "pain_score", nullable = false)
    private Integer painScore;

    @Column(name = "remarks", columnDefinition = "text")
    private String remarks;

    @Column(name = "doctor_notified", nullable = false)
    private Boolean doctorNotified = false;

    @Column(name = "doctor_name", length = 100)
    private String doctorName;

    @Column(name = "doctor_advice", columnDefinition = "text")
    private String doctorAdvice;

    @Column(name = "patient_response", nullable = false, length = 50)
    private String patientResponse; // e.g. "IMPROVED", "STABLE", "DETERIORATED"

    @Column(name = "status", nullable = false, length = 20)
    private String status = "DRAFT"; // DRAFT / SUBMITTED

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        if (this.publicId == null) {
            this.publicId = java.util.UUID.randomUUID().toString();
        }
    }
}
