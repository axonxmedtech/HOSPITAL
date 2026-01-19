package com.hms.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.time.LocalDateTime;

@Entity
@Table(name = "medicines")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Medicine {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private Integer stockQuantity = 0; // Current available stock (Default: 0)

    private Double unitPrice; // Price per unit

    private java.time.LocalDate expiryDate; // Expiry date

    @Column(name = "min_stock_level")
    private Integer minStockLevel = 10; // Low stock alert threshold (Default: 10)

    private String type; // e.g., Tablet, Syrup, Injection, Cream

    private String defaultDosage; // e.g., 500mg

    private String defaultFrequency; // e.g., 1-0-1

    private String defaultDuration; // e.g., 5 Days

    private String manufacturer;

    @Column(name = "hospital_id")
    private Long hospitalId; // Null for global master list, set for hospital-specific

    @Column(name = "is_active")
    private Boolean isActive = true;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
