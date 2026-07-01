package com.hms.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

/**
 * PatientConsent - Shared Entity representing Consent details.
 * Supports root General Consent and acts as parent for other specialized types.
 *
 * @author HMS Team
 * @version Phase-1
 */
@Entity
@Table(name = "patient_consent")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PatientConsent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "public_id", nullable = false, unique = true, length = 36)
    private String publicId;

    @Column(name = "hospital_id", nullable = false)
    private Long hospitalId;

    @Column(name = "patient_id", nullable = false)
    private Long patientId;

    @Column(name = "admission_id")
    private Long admissionId;

    @Column(name = "encounter_type", nullable = false, length = 20)
    private String encounterType; // e.g. "IPD", "OPD", "EMERGENCY"

    @Column(name = "consent_type", nullable = false, length = 30)
    private String consentType; // e.g. "GENERAL", "BLOOD", "SURGERY"

    @Column(name = "version", nullable = false)
    private Integer version = 1;

    @Column(name = "parent_id")
    private Long parentId;

    @Column(name = "status", nullable = false, length = 20)
    private String status = "DRAFT"; // e.g. "DRAFT", "SIGNED", "SUBMITTED", "LOCKED", "SUPERSEDED"

    @Column(name = "patient_signed", nullable = false)
    private Boolean patientSigned = false;

    @Column(name = "guardian_signed", nullable = false)
    private Boolean guardianSigned = false;

    @Column(name = "relationship", length = 40)
    private String relationship;

    @Column(name = "signature_type", length = 30)
    private String signatureType; // e.g. "FINGER", "STYLUS", "OTP"

    @Column(name = "witness_id")
    private Long witnessId;

    @Column(name = "language", nullable = false, length = 20)
    private String language = "English";

    @Column(name = "interpreter_id")
    private Long interpreterId;

    @Column(name = "signed_at")
    private LocalDateTime signedAt;

    @Column(name = "remarks", columnDefinition = "text")
    private String remarks;

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "is_deleted", nullable = false)
    private Boolean isDeleted = false;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
        if (this.publicId == null) {
            this.publicId = java.util.UUID.randomUUID().toString();
        }
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
