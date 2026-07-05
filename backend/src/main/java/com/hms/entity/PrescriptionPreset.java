package com.hms.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

/**
 * A per-hospital, named bundle of medicines a doctor can apply to a
 * patient's prescription in one action instead of re-typing each medicine.
 * The medicine rows themselves live in PrescriptionPresetItem.
 */
@Entity
@Table(name = "prescription_presets")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PrescriptionPreset {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "hospital_id", nullable = false)
    private Long hospitalId;

    /**
     * Owning doctor. NULL = shared preset (visible to every doctor in the
     * hospital); a set value scopes the preset privately to that doctor.
     */
    @Column(name = "doctor_id")
    private Long doctorId;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(name = "display_order", nullable = false)
    private Integer displayOrder = 0;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
