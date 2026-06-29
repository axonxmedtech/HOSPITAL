package com.hms.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import java.time.LocalDateTime;

/**
 * LabTechnician — Profile entity for lab technician users.
 * Mirrors the Nurse entity pattern: a User row holds auth/role,
 * this row holds the lab-specific profile data.
 */
@Entity
@Table(name = "lab_technicians")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LabTechnician {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String publicId;

    /** Human-readable sequential ID: LT1, LT2, … */
    @Column(name = "custom_id")
    private String customId;

    @PrePersist
    public void generateIds() {
        if (this.publicId == null) this.publicId = java.util.UUID.randomUUID().toString();
    }

    @Column(name = "hospital_id", nullable = false)
    private Long hospitalId;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false, length = 100)
    private String email;

    @Column(length = 15)
    private String phone;

    @Column(nullable = false)
    private Boolean isActive = true;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public Boolean getIsActive() { return isActive; }
    public void setIsActive(Boolean isActive) { this.isActive = isActive; }
}
