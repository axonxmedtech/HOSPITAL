package com.hms.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * Clinical Handover (Form 22 core) — the post-surgery OT/PACU -> ward transfer handover.
 * 1:1 with an {@link OtBooking}. Distinct from {@link ShiftHandover} (nurse shift-to-shift).
 * Initiated once the PACU record is recovery-ready (Aldrete >= 9); locked once the receiving
 * nurse accepts (BR-6). Additive/nullable columns (Hibernate ddl-auto creates them).
 */
@Entity
@Table(name = "clinical_handover")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ClinicalHandover {

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

    @Column(name = "from_department", length = 50)
    private String fromDepartment;

    @Column(name = "to_department", length = 50)
    private String toDepartment;

    @Column(name = "transport_mode", length = 30)
    private String transportMode;        // STRETCHER, WHEELCHAIR, ICU_BED, AMBULATORY

    @Column(name = "transport_staff", length = 100)
    private String transportStaff;

    @Column(name = "transfer_time")
    private LocalDateTime transferTime;

    @Column(name = "accepted_time")
    private LocalDateTime acceptedTime;

    // BR-5 device tracking (free-text/JSON list of tubes/drains/lines + functional flags)
    @Column(name = "devices", columnDefinition = "text")
    private String devices;

    @Column(name = "monitoring_plan", columnDefinition = "text")
    private String monitoringPlan;

    @Column(name = "pending_tasks", columnDefinition = "text")
    private String pendingTasks;

    @Column(name = "remarks", columnDefinition = "text")
    private String remarks;

    @Column(name = "handover_by", length = 100)
    private String handoverBy;

    @Column(name = "accepted_by", length = 100)
    private String acceptedBy;

    // PENDING, ACCEPTED, CANCELLED
    @Column(name = "status", length = 20)
    private String status;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
