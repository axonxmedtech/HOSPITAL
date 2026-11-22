package com.hms.entity.pharmacy;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/** One line of a supplier return note. */
@Entity
@Table(name = "supplier_return_note_items")
@Data
@NoArgsConstructor
public class SupplierReturnNoteItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "medicine_batch_id", nullable = false, updatable = false)
    private Long medicineBatchId;

    @Column(name = "batch_number", length = 60, updatable = false)
    private String batchNumber;

    @Column(name = "medicine_name", length = 200, updatable = false)
    private String medicineName;

    @Column(name = "quantity", nullable = false, precision = 12, scale = 2, updatable = false)
    private BigDecimal quantity;

    @Column(name = "rate", nullable = false, precision = 12, scale = 2, updatable = false)
    private BigDecimal rate;

    @Column(name = "amount", nullable = false, precision = 12, scale = 2, updatable = false)
    private BigDecimal amount;
}
