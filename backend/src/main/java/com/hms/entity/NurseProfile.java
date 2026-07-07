package com.hms.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * NurseProfile - thin staff profile for a NURSE user (Phase 1 Nurse module).
 *
 * Mirrors the Receptionist/Pharmacist profile pattern: credentials live on the
 * {@code users} table (role = NURSE); this record holds the nurse-facing staff
 * details. Hospital-scoped; nurse is a HOSPITAL-tenant-only role.
 */
@Entity
@Table(name = "nurse_profiles")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class NurseProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String publicId;

    @Column(name = "custom_id")
    private String customId;

    /** FK to the backing users row (role = NURSE). */
    @Column(name = "user_id")
    private Long userId;

    @Column(name = "hospital_id", nullable = false)
    private Long hospitalId;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(length = 20)
    private String phone;

    @Column(nullable = false, length = 100)
    private String email;

    @Column(name = "license_number", length = 50)
    private String licenseNumber;

    /** Ward this nurse is assigned to (FK to wards.ward_id). One ward, many nurses. */
    @Column(name = "ward_id")
    private Long wardId;

    /**
     * Whether the nurse is currently on shift. A nurse must start their shift
     * after login to become available; ending the shift makes them unavailable
     * for auto-assignment of new admissions.
     */
    @Column(name = "on_shift", nullable = false)
    private Boolean onShift = false;

    @Column(nullable = false)
    private Boolean isActive = true;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    public void generateIds() {
        if (this.publicId == null) {
            this.publicId = java.util.UUID.randomUUID().toString();
        }
    }
}
