package com.hms.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** One step in a claim's timeline (submitted, query, approved for X, settled ...). Append-only. */
@Entity
@Table(name = "ipd_insurance_claim_events")
@Data
@NoArgsConstructor
public class IpdInsuranceClaimEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "hospital_id", nullable = false)
    private Long hospitalId;

    @Column(name = "claim_id", nullable = false)
    private Long claimId;

    @Column(nullable = false, length = 30)
    private String status;

    @Column(precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(length = 500)
    private String note;

    @Column(name = "created_by")
    private String createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = LocalDateTime.now(java.time.ZoneId.systemDefault());
    }
}
