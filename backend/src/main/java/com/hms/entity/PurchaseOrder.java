package com.hms.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "purchase_order")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PurchaseOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "hospital_id", nullable = false)
    private Long hospitalId;

    @Column(name = "vendor_id", nullable = false)
    private Long vendorId;

    @Column(name = "po_number", nullable = false, length = 25)
    private String poNumber;

    @Column(nullable = false, length = 20)
    private String status = "DRAFT"; // DRAFT / SENT / PARTIALLY_RECEIVED / RECEIVED / CANCELLED

    @Column(name = "approved_by")
    private Long approvedBy;

    @Column(name = "approved_by_sig")
    private String approvedBySig;

    @Column(name = "order_date", nullable = false)
    private LocalDateTime orderDate = LocalDateTime.now();

    @Column(name = "expected_delivery")
    private LocalDate expectedDelivery;

    @Column(name = "items_json", columnDefinition = "TEXT")
    private String itemsJson; // JSON array: [{"itemId": 3, "quantity": 1000, "rate": 5.20}]
}
