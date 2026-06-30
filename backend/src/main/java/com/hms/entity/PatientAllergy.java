package com.hms.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import java.time.LocalDateTime;

@Entity
@Table(name = "patient_allergies")
@Data @NoArgsConstructor @AllArgsConstructor
public class PatientAllergy {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "hospital_id", nullable = false)
    private Long hospitalId;

    @Column(name = "patient_id", nullable = false)
    private Long patientId;

    @Column(name = "allergy_master_id", nullable = false)
    private Long allergyMasterId;

    // MILD / MODERATE / SEVERE / UNKNOWN
    @Column(length = 20, nullable = false)
    private String severity = "UNKNOWN";

    @Column(columnDefinition = "text")
    private String notes;

    @Column(name = "recorded_by_user_id")
    private Long recordedByUserId;

    @CreationTimestamp
    @Column(name = "recorded_at", updatable = false)
    private LocalDateTime recordedAt;
}
