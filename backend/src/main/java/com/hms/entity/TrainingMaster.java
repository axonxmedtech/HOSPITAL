package com.hms.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/** Training course definition (Form 04 core) — the LMS catalogue entry. */
@Entity
@Table(name = "training_master")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TrainingMaster {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "hospital_id", nullable = false)
    private Long hospitalId;

    @Column(name = "title", nullable = false, length = 150)
    private String title;

    // INFECTION_CONTROL / FIRE_SAFETY / BMW / CPR / PATIENT_SAFETY / NABH / HAND_HYGIENE / MED_SAFETY / EMERGENCY_CODES / EQUIPMENT
    @Column(name = "category", nullable = false, length = 30)
    private String category;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "mandatory", nullable = false)
    private boolean mandatory;

    // 0 = never expires
    @Column(name = "validity_period", nullable = false)
    private Integer validityPeriod;

    @Column(name = "target_roles", length = 250)
    private String targetRoles;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
