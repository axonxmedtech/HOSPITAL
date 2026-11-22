package com.hms.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * An insurance / TPA claim for one admission: who pays, the pre-authorisation and final claim
 * status, and the amounts. The approved amount is the insurer's share at discharge; the
 * settlement is posted to the IPD bill as a payment of kind INSURANCE.
 */
@Entity
@Table(name = "ipd_insurance_claims")
@Data
@NoArgsConstructor
public class IpdInsuranceClaim {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "public_id", nullable = false, unique = true, length = 36)
    private String publicId;

    @Column(name = "hospital_id", nullable = false)
    private Long hospitalId;

    @Column(name = "ipd_admission_id", nullable = false)
    private Long ipdAdmissionId;

    /** TPA, INSURANCE, GOVT_SCHEME (PM-JAY, CGHS, ECHS ...) or CORPORATE. */
    @Column(name = "payer_type", nullable = false, length = 20)
    private String payerType;

    @Column(name = "insurer_name", nullable = false, length = 150)
    private String insurerName;

    @Column(name = "tpa_name", length = 150)
    private String tpaName;

    @Column(name = "policy_number", length = 80)
    private String policyNumber;

    @Column(name = "member_id", length = 80)
    private String memberId;

    @Column(name = "claim_number", length = 80)
    private String claimNumber;

    @Column(nullable = false, length = 30)
    private String status;

    @Column(name = "requested_amount", precision = 12, scale = 2)
    private BigDecimal requestedAmount;

    @Column(name = "approved_amount", precision = 12, scale = 2)
    private BigDecimal approvedAmount;

    @Column(name = "settled_amount", precision = 12, scale = 2)
    private BigDecimal settledAmount;

    @Column(name = "deduction_amount", precision = 12, scale = 2)
    private BigDecimal deductionAmount;

    @Column(name = "deduction_reason")
    private String deductionReason;

    @Column(name = "created_by")
    private String createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    void onCreate() {
        if (publicId == null) publicId = java.util.UUID.randomUUID().toString();
        if (createdAt == null) createdAt = LocalDateTime.now(java.time.ZoneId.systemDefault());
    }
}
