package com.hms.entity.pharmacy;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Entity
@Table(name = "pharmacy_sale_items")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PharmacySaleItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pharmacy_sale_id", nullable = false)
    @JsonIgnore
    private PharmacySale pharmacySale;

    @Column(name = "medicine_id", nullable = false)
    private Long medicineId;

    @Column(name = "medicine_batch_id", nullable = false)
    private Long medicineBatchId;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "medicine_batch_id", insertable = false, updatable = false)
    private MedicineBatch medicineBatch;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal quantity;

    @Column(name = "unit_price", nullable = false, precision = 15, scale = 2)
    private BigDecimal unitPrice;

    @Column(name = "tax_percentage", precision = 5, scale = 2)
    private BigDecimal taxPercentage;

    @Column(name = "tax_amount", precision = 15, scale = 2)
    private BigDecimal taxAmount;

    @Column(name = "discount_amount", precision = 15, scale = 2)
    private BigDecimal discountAmount;

    @Column(name = "total_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal totalAmount;
}
