package com.hms.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "insurance_claim")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class InsuranceClaim {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "hospital_id", nullable = false)
    private Long hospitalId;

    @Column(name = "billing_id", nullable = false)
    private Long billingId;

    @Column(name = "payer", nullable = false, length = 100)
    private String payer;

    @Column(name = "claim_amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal claimAmount;

    @Column(name = "approved_amount", precision = 10, scale = 2)
    private BigDecimal approvedAmount;

    @Column(name = "status", nullable = false, length = 30)
    private String status = "PENDING_AUTH"; // PENDING_AUTH / APPROVED / SUBMITTED / SETTLED / REJECTED

    @Column(name = "submitted_at")
    private LocalDateTime submittedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
