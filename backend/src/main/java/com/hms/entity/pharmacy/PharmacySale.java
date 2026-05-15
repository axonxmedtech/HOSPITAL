package com.hms.entity.pharmacy;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "pharmacy_sales")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PharmacySale {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "hospital_id", nullable = false)
    private Long hospitalId;

    @Column(name = "patient_id")
    private Long patientId;

    @Column(name = "bill_number", unique = true, nullable = false)
    private String billNumber;

    @Column(name = "bill_date", nullable = false)
    private LocalDateTime billDate;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal subtotal;

    @Column(name = "tax_amount", precision = 15, scale = 2)
    private BigDecimal taxAmount;

    @Column(name = "discount_amount", precision = 15, scale = 2)
    private BigDecimal discountAmount;

    @Column(name = "net_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal netAmount;

    @Column(name = "payment_status")
    private String paymentStatus; // PAID, PENDING, CANCELLED

    @Column(name = "payment_method")
    private String paymentMethod; // CASH, CARD, UPI, IPD_BILL

    @Column(name = "is_ipd_bill")
    private Boolean isIpdBill = false;

    @Column(name = "ipd_admission_id")
    private Long ipdAdmissionId;

    @Column(name = "created_by")
    private Long createdBy;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @OneToMany(mappedBy = "pharmacySale", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<PharmacySaleItem> items;

    @PrePersist
    public void generateBillNumber() {
        if (this.billNumber == null) {
            this.billNumber = "PHB-" + System.currentTimeMillis();
        }
        if (this.billDate == null) {
            this.billDate = LocalDateTime.now();
        }
    }
}
