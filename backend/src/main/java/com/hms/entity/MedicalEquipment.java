package com.hms.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Medical equipment asset register (Form 36 core). BR-1: unique asset code per asset.
 * BR-2: calibration overdue automatically locks the asset. BR-6: retired assets are never
 * deleted, only flagged RETIRED.
 */
@Entity
@Table(name = "medical_equipment")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MedicalEquipment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "hospital_id", nullable = false)
    private Long hospitalId;

    @Column(name = "asset_code", nullable = false, unique = true, length = 20)
    private String assetCode;

    @Column(name = "equipment_name", nullable = false, length = 100)
    private String equipmentName;

    @Column(name = "category", nullable = false, length = 50)
    private String category;

    @Column(name = "manufacturer", length = 100)
    private String manufacturer;

    @Column(name = "model", length = 50)
    private String model;

    @Column(name = "serial_number", nullable = false, length = 50)
    private String serialNumber;

    @Column(name = "department", nullable = false, length = 50)
    private String department;

    @Column(name = "location", length = 50)
    private String location;

    // ACTIVE / DOWN / CALIBRATION_OVERDUE / RETIRED
    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @Column(name = "warranty_expiry")
    private LocalDate warrantyExpiry;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
