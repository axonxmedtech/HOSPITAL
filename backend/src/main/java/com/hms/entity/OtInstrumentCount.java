package com.hms.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * Instrument / Swab / Needle Count (Form 23 core). 1:1 with an {@link OtBooking}.
 * Safety rule (BR-2): when a count record exists, the WHO sign-out is blocked until the
 * final count is VERIFIED or a documented discrepancy resolution is recorded. Item lines
 * are captured as a text summary in this increment (structured item table deferred).
 * Additive/nullable columns (Hibernate ddl-auto creates them).
 */
@Entity
@Table(name = "ot_instrument_count")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class OtInstrumentCount {

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

    @Column(name = "scrub_nurse", length = 100)
    private String scrubNurse;

    @Column(name = "circulating_nurse", length = 100)
    private String circulatingNurse;

    // Item lines as text for this increment: "Artery forceps 6/6; Abdominal sponges 10/10; Needles 4/4"
    @Column(name = "count_summary", columnDefinition = "text")
    private String countSummary;

    // PENDING or VERIFIED
    @Column(name = "initial_count_status", length = 20)
    private String initialCountStatus;

    // PENDING, VERIFIED or MISMATCH
    @Column(name = "final_count_status", length = 20)
    private String finalCountStatus;

    @Column(name = "discrepancy_found")
    private Boolean discrepancyFound;

    @Column(name = "resolved")
    private Boolean resolved;

    @Column(name = "search_conducted")
    private Boolean searchConducted;

    @Column(name = "xray_performed")
    private Boolean xrayPerformed;

    @Column(name = "resolution_remarks", columnDefinition = "text")
    private String resolutionRemarks;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
