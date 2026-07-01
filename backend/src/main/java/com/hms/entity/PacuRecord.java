package com.hms.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * PACU / Recovery Record (Form 20 core). 1:1 with an {@link OtBooking}.
 * Starts only after the anaesthesia record is COMPLETED (Form 19 BR-5); the modified
 * Aldrete score is server-computed; transfer to a ward is gated on Aldrete >= 9.
 * Additive/nullable columns (Hibernate ddl-auto creates them).
 */
@Entity
@Table(name = "pacu_record")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PacuRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "hospital_id", nullable = false)
    private Long hospitalId;

    @Column(name = "patient_id")
    private Long patientId;

    @Column(name = "admission_id")
    private Long admissionId;

    @Column(name = "ot_booking_id", nullable = false, unique = true)
    private Long otBookingId;

    @Column(name = "recovery_start")
    private LocalDateTime recoveryStart;

    @Column(name = "recovery_end")
    private LocalDateTime recoveryEnd;

    @Column(name = "recovery_bed", length = 20)
    private String recoveryBed;

    // Structured recovery assessment
    @Column(name = "consciousness", length = 20)
    private String consciousness;

    @Column(name = "orientation", length = 20)
    private String orientation;

    @Column(name = "airway_status", length = 20)
    private String airwayStatus;

    @Column(name = "breathing_status", length = 20)
    private String breathingStatus;

    @Column(name = "circulation_status", length = 20)
    private String circulationStatus;

    @Column(name = "nausea_severity", length = 20)
    private String nauseaSeverity;

    @Column(name = "vomiting_present")
    private Boolean vomitingPresent;

    @Column(name = "pain_score")
    private Integer painScore;

    // Modified Aldrete components (each 0, 1 or 2)
    @Column(name = "aldrete_activity")
    private Integer aldreteActivity;

    @Column(name = "aldrete_respiration")
    private Integer aldreteRespiration;

    @Column(name = "aldrete_circulation")
    private Integer aldreteCirculation;

    @Column(name = "aldrete_consciousness")
    private Integer aldreteConsciousness;

    @Column(name = "aldrete_oxygen")
    private Integer aldreteOxygen;

    // Server-computed sum (0-10)
    @Column(name = "aldrete_score")
    private Integer aldreteScore;

    @Column(name = "transfer_destination", length = 30)
    private String transferDestination;   // WARD, ICU, HDU, RE_EXPLORATION

    @Column(name = "handover_notes", columnDefinition = "text")
    private String handoverNotes;

    // ACTIVE, READY (Aldrete >= 9), TRANSFERRED
    @Column(name = "status", length = 20)
    private String status;

    @Column(name = "signed_by", length = 100)
    private String signedBy;

    @Column(name = "signed_at")
    private LocalDateTime signedAt;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
