package com.hms.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

/**
 * HospitalInventory - Entity representing physical active stock levels of non-medicine hospital inventory
 * 
 * Maps to 'hospital_inventory' table.
 */
@Entity
@Table(name = "hospital_inventory")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class HospitalInventory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(name = "stock_quantity", nullable = false)
    private Integer stockQuantity = 0;

    @Column(name = "unit_price")
    private Double unitPrice;

    @Column(name = "expiry_date")
    private java.time.LocalDate expiryDate;

    @Column(name = "min_stock_level")
    private Integer minStockLevel = 10;

    @Column(length = 50)
    private String type; // e.g. Syringe, Fluid, Consumable, Surgical, Gloves

    @Column(length = 100)
    private String manufacturer;

    @Column(name = "hospital_id", nullable = false)
    private Long hospitalId;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
