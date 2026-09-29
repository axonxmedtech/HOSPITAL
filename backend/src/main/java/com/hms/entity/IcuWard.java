package com.hms.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * IcuWard - an Intensive Care Unit ward record.
 *
 * <p>Represents a dedicated ICU ward managed within the ICU module and admin portal.
 * Linked to a base {@code Ward} entity via {@code wardId} to ensure complete backward
 * compatibility and unbroken integration with beds, IPD admissions, daily bed billing,
 * nurse assignments, and patient bed histories.
 */
@Entity
@Table(name = "icu_wards",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_icu_ward_public_id", columnNames = {"public_id"}),
                @UniqueConstraint(name = "uk_icu_ward_ward_id", columnNames = {"ward_id"}),
                @UniqueConstraint(name = "uk_icu_ward_hospital_name", columnNames = {"hospital_id", "ward_name"})
        },
        indexes = {
                @Index(name = "idx_icu_wards_hospital", columnList = "hospital_id")
        })
@Data
@NoArgsConstructor
@AllArgsConstructor
public class IcuWard {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "public_id", nullable = false, unique = true)
    private String publicId;

    @Column(name = "hospital_id", nullable = false)
    private Long hospitalId;

    /** Foreign key / link to the base wards entry. */
    @Column(name = "ward_id", nullable = false, unique = true)
    private Long wardId;

    @Column(name = "ward_name", nullable = false, length = 100)
    private String wardName;

    /** Critical care classification (ICU, MICU, SICU, NICU, PICU, CCU, HDU). */
    @Column(name = "unit_type", nullable = false, length = 20)
    private String unitType = "ICU";

    @Column(name = "bed_price", nullable = false, precision = 10, scale = 2)
    private BigDecimal bedPrice;

    @Column(name = "total_beds", nullable = false)
    private Integer totalBeds;

    @Column(name = "floor_number")
    private Integer floorNumber;

    @Column(name = "incharge_nurse_id")
    private Long inchargeNurseId;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    public void prePersist() {
        if (this.publicId == null) {
            this.publicId = java.util.UUID.randomUUID().toString();
        }
    }
}
