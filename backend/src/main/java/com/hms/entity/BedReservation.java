package com.hms.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * A free bed held for a planned admission until {@code reservedUntil}. While it holds, only the
 * reserved patient can be admitted into the bed. ACTIVE / USED / CANCELLED; an ACTIVE hold past
 * its time is simply ignored (treated as expired).
 */
@Entity
@Table(name = "bed_reservations")
@Data
@NoArgsConstructor
public class BedReservation {

    public static final String ACTIVE = "ACTIVE";
    public static final String USED = "USED";
    public static final String CANCELLED = "CANCELLED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "public_id", nullable = false, unique = true, length = 36)
    private String publicId;

    @Column(name = "hospital_id", nullable = false)
    private Long hospitalId;

    @Column(name = "ward_id", nullable = false)
    private Long wardId;

    @Column(name = "bed_id", nullable = false)
    private Long bedId;

    /** The registered patient, when the hold is for one; otherwise only name and phone. */
    @Column(name = "patient_id")
    private Long patientId;

    @Column(name = "patient_name", nullable = false, length = 150)
    private String patientName;

    @Column(name = "patient_phone", length = 20)
    private String patientPhone;

    @Column(name = "expected_at", nullable = false)
    private LocalDateTime expectedAt;

    @Column(name = "reserved_until", nullable = false)
    private LocalDateTime reservedUntil;

    @Column(length = 500)
    private String notes;

    @Column(nullable = false, length = 20)
    private String status = ACTIVE;

    @Column(name = "ipd_admission_id")
    private Long ipdAdmissionId;

    @Column(name = "cancel_reason")
    private String cancelReason;

    @Column(name = "created_by")
    private String createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void onCreate() {
        if (publicId == null) publicId = java.util.UUID.randomUUID().toString();
        if (createdAt == null) createdAt = LocalDateTime.now(java.time.ZoneId.systemDefault());
    }

    /** Holding now: active and not past its time. */
    public boolean holds(LocalDateTime now) {
        return ACTIVE.equals(status) && reservedUntil != null && reservedUntil.isAfter(now);
    }
}
