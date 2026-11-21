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
import java.time.LocalDate;
import java.time.LocalDateTime;

/** One payment made to a supplier against a purchase invoice. Append-only. */
@Entity
@Table(name = "purchase_payments", indexes = @Index(name = "idx_pp_invoice", columnList = "purchase_invoice_id"))
@Data
@NoArgsConstructor
public class PurchasePayment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "hospital_id", nullable = false, updatable = false)
    private Long hospitalId;

    @Column(name = "branch_id", updatable = false)
    private Long branchId;

    @Column(name = "purchase_invoice_id", nullable = false, updatable = false)
    private Long purchaseInvoiceId;

    @Column(name = "amount", nullable = false, precision = 12, scale = 2, updatable = false)
    private BigDecimal amount;

    /** CASH, UPI, CARD, BANK_TRANSFER, CHEQUE. */
    @Column(name = "method", nullable = false, length = 20, updatable = false)
    private String method;

    @Column(name = "reference", length = 60, updatable = false)
    private String reference;

    @Column(name = "paid_on", nullable = false, updatable = false)
    private LocalDate paidOn;

    @Column(name = "created_by", updatable = false)
    private Long createdBy;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
