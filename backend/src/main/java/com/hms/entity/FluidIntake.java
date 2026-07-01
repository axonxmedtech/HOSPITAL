package com.hms.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

/**
 * FluidIntake - Entity representing a single fluid intake record.
 * Supports manual entries or derived entries (e.g. from MAR NurseTask or BloodBank).
 *
 * @author HMS Team
 * @version Phase-1
 */
@Entity
@Table(name = "fluid_intake")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class FluidIntake {

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

    @Column(name = "type", nullable = false, length = 30)
    private String type; // ORAL / IV / TUBE / BLOOD

    @Column(name = "source_ref")
    private Long sourceRef; // NurseTask ID or BloodBank record reference

    @Column(name = "description", nullable = false, length = 255)
    private String description;

    @Column(name = "volume_ml", nullable = false)
    private Integer volumeMl;

    @Column(name = "recorded_time", nullable = false)
    private LocalDateTime recordedTime;

    @Column(name = "recorded_by", nullable = false)
    private Long recordedBy;

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
