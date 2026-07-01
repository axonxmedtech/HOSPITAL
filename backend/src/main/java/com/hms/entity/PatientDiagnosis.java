package com.hms.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

/**
 * PatientDiagnosis - Entity representing a diagnosis logged for an IPD encounter.
 * Keeps an append-only timeline of diagnoses.
 *
 * @author HMS Team
 * @version Phase-1
 */
@Entity
@Table(name = "patient_diagnosis")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PatientDiagnosis {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "hospital_id", nullable = false)
    private Long hospitalId;

    @Column(name = "patient_id", nullable = false)
    private Long patientId;

    @Column(name = "admission_id", nullable = false)
    private Long admissionId;

    @Column(name = "diagnosis_code", length = 30)
    private String diagnosisCode; // ICD code

    @Column(name = "diagnosis_description", nullable = false, columnDefinition = "text")
    private String diagnosisDescription;

    @Column(name = "diagnosis_type", nullable = false, length = 20)
    private String diagnosisType; // PROVISIONAL / FINAL

    @Column(name = "recorded_by", nullable = false)
    private Long recordedBy;

    @Column(name = "recorded_at", nullable = false)
    private LocalDateTime recordedAt;

    @PrePersist
    protected void onCreate() {
        this.recordedAt = LocalDateTime.now();
    }
}
