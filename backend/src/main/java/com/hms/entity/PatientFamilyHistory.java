package com.hms.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

/**
 * PatientFamilyHistory - Entity representing family disease history for a patient.
 * Keyed by patientId, reusable across future admissions (EMR backbone).
 *
 * @author HMS Team
 * @version Phase-1
 */
@Entity
@Table(name = "patient_family_history")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PatientFamilyHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "hospital_id", nullable = false)
    private Long hospitalId;

    @Column(name = "patient_id", nullable = false)
    private Long patientId;

    @Column(name = "relationship", nullable = false, length = 50)
    private String relationship; // e.g. "Father", "Mother"

    @Column(name = "condition_name", nullable = false, length = 100)
    private String conditionName; // e.g. "Diabetes", "Stroke"

    @Column(name = "recorded_at", nullable = false)
    private LocalDateTime recordedAt;

    @PrePersist
    protected void onCreate() {
        this.recordedAt = LocalDateTime.now();
    }
}
