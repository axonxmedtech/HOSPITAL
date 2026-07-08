package com.hms.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Surgery - an Operation Theatre case for an IPD patient (OT module, Phase 2).
 * Own status lifecycle; IpdAdmission.status is untouched. Requested by a doctor,
 * scheduled/run by reception. One active (non-terminal) surgery per admission.
 */
@Entity
@Table(name = "surgeries")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Surgery {

    public static final String REQUESTED = "REQUESTED";
    public static final String SCHEDULED = "SCHEDULED";
    public static final String IN_PROGRESS = "IN_PROGRESS";
    public static final String COMPLETED = "COMPLETED";
    public static final String CANCELLED = "CANCELLED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String publicId;

    @Column(name = "hospital_id", nullable = false)
    private Long hospitalId;

    @Column(name = "ipd_admission_id", nullable = false)
    private Long ipdAdmissionId;

    @Column(name = "patient_id", nullable = false)
    private Long patientId;

    @Column(name = "procedure_name", length = 255)
    private String procedureName;

    @Column(name = "clinical_notes", columnDefinition = "text")
    private String clinicalNotes;

    @Column(name = "priority", length = 20)
    private String priority; // ELECTIVE | EMERGENCY

    @Column(name = "preferred_date")
    private LocalDate preferredDate;

    @Column(name = "requested_by_doctor_id")
    private Long requestedByDoctorId;

    @Column(name = "requested_by_user_id")
    private Long requestedByUserId;

    @Column(name = "requested_at", nullable = false)
    private LocalDateTime requestedAt;

    @Column(name = "status", nullable = false, length = 20)
    private String status = REQUESTED;

    @Column(name = "surgeon_doctor_id")
    private Long surgeonDoctorId;

    // Display name of the operator: usually the assigned doctor's name, but may be
    // a free-text "Other" operator (e.g. external / anaesthetist-led) when no listed
    // doctor is assigned (surgeonDoctorId is null).
    @Column(name = "surgeon_name", length = 255)
    private String surgeonName;

    // Optional anaesthetist present for the surgery (free text).
    @Column(name = "anaesthetist_name", length = 255)
    private String anaesthetistName;

    @Column(name = "scheduled_at")
    private LocalDateTime scheduledAt;

    @Column(name = "ot_ward_id")
    private Long otWardId;

    @Column(name = "ot_bed_id")
    private Long otBedId;

    @Column(name = "scheduled_by_user_id")
    private Long scheduledByUserId;

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    public void prePersist() {
        if (this.publicId == null) this.publicId = java.util.UUID.randomUUID().toString();
        if (this.requestedAt == null) this.requestedAt = LocalDateTime.now();
        if (this.status == null) this.status = REQUESTED;
    }
}
