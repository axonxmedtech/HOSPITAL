package com.hms.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Refund Sign-off (Form 30 BR-5). A refund against a bill requires an approved
 * supervisor (HOSPITAL_ADMIN) sign-off with a documented reason before it is honoured.
 * Additive/nullable columns.
 */
@Entity
@Table(name = "billing_refund")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class BillingRefund {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "hospital_id", nullable = false)
    private Long hospitalId;

    @Column(name = "billing_id", nullable = false)
    private Long billingId;

    @Column(name = "patient_id")
    private Long patientId;

    @Column(name = "amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal amount;

    @Column(name = "reason", nullable = false, columnDefinition = "text")
    private String reason;

    @Column(name = "requested_by_name", nullable = false, length = 100)
    private String requestedByName;

    @Column(name = "requested_at", nullable = false)
    private LocalDateTime requestedAt;

    // PENDING, APPROVED, REJECTED
    @Column(name = "status", length = 20)
    private String status;

    @Column(name = "approved_by_name", length = 100)
    private String approvedByName;

    @Column(name = "approved_at")
    private LocalDateTime approvedAt;

    @Column(name = "rejection_reason", columnDefinition = "text")
    private String rejectionReason;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
