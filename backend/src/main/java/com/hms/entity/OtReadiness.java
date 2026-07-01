package com.hms.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * OT Cleaning, Sterility & OT Readiness Checklist (Form 26 core).
 * Tracks room sterility/readiness state per date and room.
 * Gated by do-no-harm logic: only locks scheduleBooking if a record exists and is not READY.
 */
@Entity
@Table(name = "ot_readiness")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class OtReadiness {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "hospital_id", nullable = false)
    private Long hospitalId;

    @Column(name = "ot_room", nullable = false, length = 50)
    private String otRoom;

    @Column(name = "readiness_date", nullable = false)
    private LocalDate readinessDate;

    // manual check stubs
    @Column(name = "cleaning_done")
    private Boolean cleaningDone;

    @Column(name = "sterility_done")
    private Boolean sterilityDone;

    @Column(name = "equipment_ok")
    private Boolean equipmentOk;

    @Column(name = "status", nullable = false, length = 20)
    private String status; // PENDING / READY

    @Column(name = "verified_by", length = 100)
    private String verifiedBy;

    @Column(name = "verified_at")
    private LocalDateTime verifiedAt;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
