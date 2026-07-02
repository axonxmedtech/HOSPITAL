package com.hms.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Patient Advance Deposit (Form 30 BR-7). Recorded against an IPD admission; the remaining
 * balance is auto-deducted from the final bill at discharge checkout via
 * {@code BillingService.applyAdvanceToIpdBill}. Additive/nullable columns.
 */
@Entity
@Table(name = "patient_advance")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PatientAdvance {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "hospital_id", nullable = false)
    private Long hospitalId;

    @Column(name = "patient_id", nullable = false)
    private Long patientId;

    @Column(name = "ipd_admission_id", nullable = false)
    private Long ipdAdmissionId;

    @Column(name = "amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal amount;

    // Remaining amount not yet applied to a bill
    @Column(name = "balance", nullable = false, precision = 10, scale = 2)
    private BigDecimal balance;

    @Column(name = "payment_mode", length = 30)
    private String paymentMode;

    @Column(name = "received_by_name", length = 100)
    private String receivedByName;

    @Column(name = "received_at")
    private LocalDateTime receivedAt;

    // ACTIVE (has balance), CONSUMED (balance = 0)
    @Column(name = "status", length = 20)
    private String status;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
