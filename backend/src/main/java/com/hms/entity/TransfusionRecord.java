package com.hms.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * Transfusion Record (Form 38 core, BR-5/BR-6) — bedside nurse checkoff for an issued unit.
 * A reaction other than NONE freezes the patient's active transfusion logs and quarantines
 * adjacent bags from the same donor (handled in {@code BloodBankService.recordReaction}).
 */
@Entity
@Table(name = "transfusion_record")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TransfusionRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "hospital_id", nullable = false)
    private Long hospitalId;

    @Column(name = "patient_id", nullable = false)
    private Long patientId;

    @Column(name = "blood_unit_id", nullable = false, unique = true)
    private Long bloodUnitId;

    @Column(name = "started_at", nullable = false)
    private LocalDateTime startedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    // NONE / FEBRILE / ALLERGIC / HEMOLYTIC
    @Column(name = "reaction", nullable = false, length = 30)
    private String reaction;

    @Column(name = "reaction_notes", columnDefinition = "text")
    private String reactionNotes;

    @Column(name = "nurse_name", nullable = false, length = 100)
    private String nurseName;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
