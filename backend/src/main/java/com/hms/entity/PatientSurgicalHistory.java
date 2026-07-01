package com.hms.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

/**
 * PatientSurgicalHistory - Entity representing long-term surgical history for a patient.
 * Keyed by patientId, reusable across future admissions (EMR backbone).
 *
 * @author HMS Team
 * @version Phase-1
 */
@Entity
@Table(name = "patient_surgical_history")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PatientSurgicalHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "hospital_id", nullable = false)
    private Long hospitalId;

    @Column(name = "patient_id", nullable = false)
    private Long patientId;

    @Column(name = "procedure_name", nullable = false, length = 150)
    private String procedureName; // e.g. "Appendectomy"

    @Column(name = "surgery_year")
    private Integer surgeryYear;

    @Column(name = "hospital_name", length = 100)
    private String hospitalName;

    @Column(name = "complications", columnDefinition = "text")
    private String complications;

    @Column(name = "recorded_at", nullable = false)
    private LocalDateTime recordedAt;

    @PrePersist
    protected void onCreate() {
        this.recordedAt = LocalDateTime.now();
    }
}
