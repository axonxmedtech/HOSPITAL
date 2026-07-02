package com.hms.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "purchase_requisition")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PurchaseRequisition {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "public_id", nullable = false, unique = true, length = 50)
    private String publicId;

    @Column(name = "hospital_id", nullable = false)
    private Long hospitalId;

    @Column(nullable = false, length = 50)
    private String department;

    @Column(name = "requested_by", nullable = false)
    private Long requestedBy;

    @Column(nullable = false, length = 20)
    private String priority; // ROUTINE / URGENT / EMERGENCY

    @Column(nullable = false, length = 20)
    private String status = "PENDING_APPROVAL"; // DRAFT / PENDING_APPROVAL / APPROVED / CONVERTED_TO_PO

    @Column(name = "required_date", nullable = false)
    private LocalDate requiredDate;

    @Column(name = "items_json", columnDefinition = "TEXT")
    private String itemsJson; // JSON array of requested items: [{"itemId": 3, "quantity": 1000}]

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}
