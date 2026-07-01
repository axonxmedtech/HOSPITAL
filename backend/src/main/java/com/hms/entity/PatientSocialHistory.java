package com.hms.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

/**
 * PatientSocialHistory - Entity representing social habits and lifestyles for a patient.
 * Keyed by patientId, reusable across future admissions (EMR backbone).
 *
 * @author HMS Team
 * @version Phase-1
 */
@Entity
@Table(name = "patient_social_history")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PatientSocialHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "hospital_id", nullable = false)
    private Long hospitalId;

    @Column(name = "patient_id", nullable = false)
    private Long patientId;

    @Column(name = "smoking_status", length = 30)
    private String smokingStatus; // e.g. "NEVER", "FORMER", "ACTIVE"

    @Column(name = "alcohol_consumption", length = 30)
    private String alcoholConsumption;

    @Column(name = "tobacco_use", length = 30)
    private String tobaccoUse;

    @Column(name = "dietary_habits", length = 100)
    private String dietaryHabits;

    @Column(name = "recorded_at", nullable = false)
    private LocalDateTime recordedAt;

    @PrePersist
    protected void onCreate() {
        this.recordedAt = LocalDateTime.now();
    }
}
