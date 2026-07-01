package com.hms.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

/**
 * FluidOutput - Entity representing a single fluid output observation.
 *
 * @author HMS Team
 * @version Phase-1
 */
@Entity
@Table(name = "fluid_output")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class FluidOutput {

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
    private String type; // URINE / STOOL / VOMIT / DRAIN / DIALYSIS

    @Column(name = "description", nullable = false, length = 255)
    private String description;

    @Column(name = "volume_ml", nullable = false)
    private Integer volumeMl;

    @Column(name = "color", length = 50)
    private String color;

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
