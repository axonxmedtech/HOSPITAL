package com.hms.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "billing_payments")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class BillingPayment {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "billing_id", nullable = false)
    private Long billingId;

    @Column(name = "hospital_id", nullable = false)
    private Long hospitalId;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal amount;

    @Column(length = 50)
    private String mode;

    @Column(length = 200)
    private String reference;

    /** PAYMENT (money in) or REFUND (money out, stored as a negative amount). */
    @Column(length = 10)
    private String kind = "PAYMENT";

    /** Who took (or paid back) the money, for the daily cash-closing report. */
    @Column(name = "recorded_by", length = 150)
    private String recordedBy;

    /** Refund reason, or a note on the collection. */
    @Column(length = 255)
    private String note;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
