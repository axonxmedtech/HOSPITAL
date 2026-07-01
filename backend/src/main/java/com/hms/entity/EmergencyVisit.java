package com.hms.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * Emergency Visit (Form 12 core / EIS) — the ER encounter, analogous to Opd.
 * Holds triage + primary (ABC/GCS) assessment + MLC flag + disposition for this
 * increment (separate triage-history/injury/event child tables deferred).
 * BR-2/BR-3: unknown arrivals get a temporary Patient (is_unknown/is_temporary)
 * that is later merged to a permanent record via PatientService.mergePatients.
 */
@Entity
@Table(name = "emergency_visit")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class EmergencyVisit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "hospital_id", nullable = false)
    private Long hospitalId;

    @Column(name = "patient_id", nullable = false)
    private Long patientId;

    @Column(name = "emergency_number", length = 30)
    private String emergencyNumber;

    @Column(name = "arrival_time")
    private LocalDateTime arrivalTime;

    // AMBULANCE, WALK_IN, POLICE, REFERRAL, OTHER
    @Column(name = "arrival_mode", length = 30)
    private String arrivalMode;

    // Triage (BR-1): RED, ORANGE, YELLOW, GREEN, BLACK
    @Column(name = "triage_level", length = 10)
    private String triageLevel;

    @Column(name = "triage_criteria", length = 255)
    private String triageCriteria;

    @Column(name = "triaged_by", length = 100)
    private String triagedBy;

    @Column(name = "triaged_at")
    private LocalDateTime triagedAt;

    // Medico-legal case (BR-6)
    @Column(name = "is_mlc")
    private Boolean isMlc;

    @Column(name = "mlc_number", length = 50)
    private String mlcNumber;

    // Primary assessment (ABC / GCS)
    @Column(name = "chief_complaint", columnDefinition = "text")
    private String chiefComplaint;

    @Column(name = "airway_status", length = 30)
    private String airwayStatus;

    @Column(name = "breathing_status", length = 30)
    private String breathingStatus;

    @Column(name = "circulation_status", length = 30)
    private String circulationStatus;

    @Column(name = "gcs_score")
    private Integer gcsScore;

    @Column(name = "initial_diagnosis", columnDefinition = "text")
    private String initialDiagnosis;

    @Column(name = "assessed_by", length = 100)
    private String assessedBy;

    @Column(name = "assessed_at")
    private LocalDateTime assessedAt;

    // Disposition: ADMIT, ICU, OT, DISCHARGE, REFER, DEATH
    @Column(name = "disposition", length = 30)
    private String disposition;

    // Link to the IPD admission created on ADMIT/ICU disposition
    @Column(name = "ipd_admission_id")
    private Long ipdAdmissionId;

    // ACTIVE, OBSERVATION, DISPOSED
    @Column(name = "status", length = 20)
    private String status;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
