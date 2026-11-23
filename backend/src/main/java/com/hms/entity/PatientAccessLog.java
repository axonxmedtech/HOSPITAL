package com.hms.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * Who opened, printed or downloaded a patient's record, and when. Append-only: rows are never
 * updated or deleted (NoHardDeleteOfPatientDataTest), and they are kept when a facility is purged
 * (TenantTables: retained, like audit_logs).
 *
 * <p>Kept apart from {@code audit_logs} on purpose: reads are far more frequent than changes and
 * would bury the change history the admin's Audit Logs screen shows.
 */
@Entity
@Table(name = "patient_access_logs", indexes = {
        @Index(name = "idx_pal_patient", columnList = "hospital_id, patient_id, accessed_at"),
        @Index(name = "idx_pal_user", columnList = "hospital_id, user_email, accessed_at")})
@Data
@NoArgsConstructor
public class PatientAccessLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "hospital_id", nullable = false, updatable = false)
    private Long hospitalId;

    /** Null only when the record could not be traced to a patient (kept so the access still shows). */
    @Column(name = "patient_id", updatable = false)
    private Long patientId;

    /** PATIENT, PATIENT_FILE, TIMELINE, OPD_VISIT, CASE_PAPER, PRESCRIPTION, IPD_STAY, BILL, DOCUMENT … */
    @Column(name = "record_type", nullable = false, length = 30, updatable = false)
    private String recordType;

    /** The id the user asked for, as they asked for it (OPD-12, a public id, an admission id). */
    @Column(name = "record_ref", length = 64, updatable = false)
    private String recordRef;

    /** VIEW, PRINT or DOWNLOAD. */
    @Column(name = "action", nullable = false, length = 20, updatable = false)
    private String action;

    @Column(name = "user_id", updatable = false)
    private Long userId;

    @Column(name = "user_email", nullable = false, length = 150, updatable = false)
    private String userEmail;

    @Column(name = "user_role", length = 30, updatable = false)
    private String userRole;

    @Column(name = "client_ip", length = 45, updatable = false)
    private String clientIp;

    @CreationTimestamp
    @Column(name = "accessed_at", nullable = false, updatable = false)
    private LocalDateTime accessedAt;
}
