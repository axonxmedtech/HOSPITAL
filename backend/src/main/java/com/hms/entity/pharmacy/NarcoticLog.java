package com.hms.entity.pharmacy;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Narcotic Log (Form 29 BR-4) — the legal accounting register for controlled substance
 * (schedule X / high-alert) dispenses. One row per narcotic sale item; requires a second
 * witness signature captured at dispense time. Additive/nullable columns.
 */
@Entity
@Table(name = "narcotic_log")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class NarcoticLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "hospital_id", nullable = false)
    private Long hospitalId;

    @Column(name = "pharmacy_sale_id", nullable = false)
    private Long pharmacySaleId;

    @Column(name = "medicine_id", nullable = false)
    private Long medicineId;

    @Column(name = "batch_id", nullable = false)
    private Long batchId;

    @Column(name = "quantity_issued", nullable = false, precision = 10, scale = 2)
    private BigDecimal quantityIssued;

    @Column(name = "quantity_wasted", precision = 10, scale = 2)
    private BigDecimal quantityWasted;

    @Column(name = "witness_user_id", nullable = false)
    private Long witnessUserId;

    @Column(name = "witness_name", nullable = false, length = 100)
    private String witnessName;

    @Column(name = "dispensed_by_name", nullable = false, length = 100)
    private String dispensedByName;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
