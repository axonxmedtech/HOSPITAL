package com.hms.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "department_indent")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DepartmentIndent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "hospital_id", nullable = false)
    private Long hospitalId;

    @Column(name = "from_department", nullable = false, length = 50)
    private String fromDepartment;

    @Column(name = "inventory_item_id", nullable = false)
    private Long inventoryItemId;

    @Column(name = "requested_qty", nullable = false, precision = 10, scale = 2)
    private BigDecimal requestedQty;

    @Column(nullable = false, length = 20)
    private String status = "PENDING"; // DRAFT / PENDING / APPROVED / FILLED

    @Column(name = "requested_by")
    private Long requestedBy;

    @Column(name = "approved_by")
    private Long approvedBy;

    @Column(name = "approved_by_sig")
    private String approvedBySig;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}
