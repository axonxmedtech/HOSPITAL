package com.hms.entity.pharmacy;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * A numbered return note (SR/26-27/00001) for medicine sent back to a supplier: which batches,
 * how many, at what rate, and the total claimed. The document the supplier is asked to credit.
 */
@Entity
@Table(name = "supplier_return_notes", uniqueConstraints = @UniqueConstraint(
        name = "uk_supplier_return_note_number", columnNames = {"hospital_id", "note_number"}))
@Data
@NoArgsConstructor
public class SupplierReturnNote {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "hospital_id", nullable = false, updatable = false)
    private Long hospitalId;

    @Column(name = "branch_id", updatable = false)
    private Long branchId;

    @Column(name = "note_number", nullable = false, length = 20, updatable = false)
    private String noteNumber;

    @Column(name = "supplier_id", nullable = false, updatable = false)
    private Long supplierId;

    @Column(name = "total_claimed", nullable = false, precision = 12, scale = 2, updatable = false)
    private BigDecimal totalClaimed;

    @Column(name = "reason", length = 255, updatable = false)
    private String reason;

    @Column(name = "created_by", updatable = false)
    private Long createdBy;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @OneToMany(cascade = CascadeType.ALL)
    @JoinColumn(name = "note_id", nullable = false)
    private List<SupplierReturnNoteItem> items = new ArrayList<>();
}
