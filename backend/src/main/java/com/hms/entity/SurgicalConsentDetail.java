package com.hms.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * Surgical Consent detail (Form 16) — type-specific attributes for a
 * {@link PatientConsent} of consent_type SURGERY. Mirrors the BloodConsentDetail pattern.
 */
@Entity
@Table(name = "surgical_consent_detail")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SurgicalConsentDetail {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "consent_id", nullable = false, unique = true)
    private Long consentId;

    @Column(name = "procedure_name", length = 200)
    private String procedureName;

    @Column(name = "surgeon_name", length = 100)
    private String surgeonName;

    @Column(name = "planned_anaesthesia", length = 100)
    private String plannedAnaesthesia;

    @Column(name = "risks_explained", nullable = false)
    private Boolean risksExplained = false;

    @Column(name = "alternatives_explained", nullable = false)
    private Boolean alternativesExplained = false;

    @Column(name = "high_risk", nullable = false)
    private Boolean highRisk = false;

    @Column(name = "ot_booking_id")
    private Long otBookingId;

    @Column(name = "remarks", columnDefinition = "text")
    private String remarks;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
