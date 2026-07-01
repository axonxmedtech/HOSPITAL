package com.hms.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

/**
 * PatientMedicationHistory - Entity representing current/long-term medications for a patient.
 * Keyed by patientId, reusable across future admissions (EMR backbone).
 *
 * @author HMS Team
 * @version Phase-1
 */
@Entity
@Table(name = "patient_medication_history")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PatientMedicationHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "hospital_id", nullable = false)
    private Long hospitalId;

    @Column(name = "patient_id", nullable = false)
    private Long patientId;

    @Column(name = "medication_name", nullable = false, length = 150)
    private String medicationName; // e.g. "Metformin 500mg"

    @Column(name = "dosage", length = 50)
    private String dosage;

    @Column(name = "frequency", length = 50)
    private String frequency;

    @Column(name = "compliance_status", length = 30)
    private String complianceStatus; // e.g. "COMPLIANT", "NON_COMPLIANT"

    @Column(name = "recorded_at", nullable = false)
    private LocalDateTime recordedAt;

    @PrePersist
    protected void onCreate() {
        this.recordedAt = LocalDateTime.now();
    }
}
