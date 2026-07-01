package com.hms.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

/**
 * PatientMedicalHistory - Entity representing long-term medical conditions for a patient.
 * Keyed by patientId, reusable across future admissions (EMR backbone).
 *
 * @author HMS Team
 * @version Phase-1
 */
@Entity
@Table(name = "patient_medical_history")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PatientMedicalHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "hospital_id", nullable = false)
    private Long hospitalId;

    @Column(name = "patient_id", nullable = false)
    private Long patientId;

    @Column(name = "condition_name", nullable = false, length = 100)
    private String conditionName; // e.g. "Diabetes", "Hypertension"

    @Column(name = "diagnosed_year")
    private Integer diagnosedYear;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    @Column(name = "recorded_at", nullable = false)
    private LocalDateTime recordedAt;

    @PrePersist
    protected void onCreate() {
        this.recordedAt = LocalDateTime.now();
    }
}
