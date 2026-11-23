package com.hms.entity.pharmacy;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * The last invoice number used in one series. A series is one pharmacy (or one branch of a
 * multi-branch pharmacy) in one financial year, e.g. {@code PH/26-27}. GST rules want invoice
 * numbers that are unique and consecutive within a financial year; the row is locked while a
 * number is taken, so two counters billing at once never get the same number or skip one.
 */
@Entity
@Table(name = "pharmacy_invoice_sequences",
        uniqueConstraints = @UniqueConstraint(name = "uk_pharmacy_invoice_series", columnNames = {"hospital_id", "series"}))
@Data
@NoArgsConstructor
public class PharmacyInvoiceSequence {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "hospital_id", nullable = false)
    private Long hospitalId;

    @Column(name = "series", nullable = false, length = 20)
    private String series;

    @Column(name = "last_number", nullable = false)
    private Long lastNumber = 0L;
}
