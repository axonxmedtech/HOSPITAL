package com.hms.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * A fixed-price inpatient package ("Normal delivery — Rs 25,000, 3 days"). It covers bed, nursing
 * and doctor visits for its included days; medicines, consumables and further days are charged
 * as usual. Never deleted: retired packages are made inactive, because admissions refer to them.
 */
@Entity
@Table(name = "ipd_packages")
@Data
@NoArgsConstructor
public class IpdPackage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "hospital_id", nullable = false)
    private Long hospitalId;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal price;

    @Column(name = "included_days", nullable = false)
    private Integer includedDays;

    @Column(length = 500)
    private String description;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = LocalDateTime.now(java.time.ZoneId.systemDefault());
    }
}
