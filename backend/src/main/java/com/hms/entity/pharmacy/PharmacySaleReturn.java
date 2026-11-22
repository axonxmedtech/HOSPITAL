package com.hms.entity.pharmacy;

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

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * One customer refund: which bill line came back, how many, and the money returned (GST included)
 * with the GST part of it. Reports read refunds from here; the stock ledger only records that the
 * medicine did not go back on the shelf. Append-only.
 */
@Entity
@Table(name = "pharmacy_sale_returns", indexes = {
        @Index(name = "idx_psr_hospital_date", columnList = "hospital_id, created_at"),
        @Index(name = "idx_psr_sale", columnList = "sale_id")})
@Data
@NoArgsConstructor
public class PharmacySaleReturn {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "hospital_id", nullable = false, updatable = false)
    private Long hospitalId;

    @Column(name = "branch_id", updatable = false)
    private Long branchId;

    @Column(name = "sale_id", nullable = false, updatable = false)
    private Long saleId;

    @Column(name = "sale_item_id", nullable = false, updatable = false)
    private Long saleItemId;

    @Column(name = "medicine_batch_id", nullable = false, updatable = false)
    private Long medicineBatchId;

    @Column(name = "quantity", nullable = false, precision = 12, scale = 2, updatable = false)
    private BigDecimal quantity;

    /** Money given back, GST included. */
    @Column(name = "refund_amount", nullable = false, precision = 12, scale = 2, updatable = false)
    private BigDecimal refundAmount;

    /** The GST contained in refund_amount (reduces the GST payable). */
    @Column(name = "gst_amount", nullable = false, precision = 12, scale = 2, updatable = false)
    private BigDecimal gstAmount;

    @Column(name = "created_by", updatable = false)
    private Long createdBy;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
