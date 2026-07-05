package com.hms.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * One medicine row within a PrescriptionPreset. Field names/lengths
 * deliberately mirror Prescription.java so an item maps 1:1 onto a
 * prescription row with no field-name translation needed.
 */
@Entity
@Table(name = "prescription_preset_items")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PrescriptionPresetItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "preset_id", nullable = false)
    private Long presetId;

    @Column(name = "medicine_name", nullable = false)
    private String medicineName;

    @Column(length = 50)
    private String dosage;

    @Column(length = 50)
    private String frequency;

    @Column(length = 50)
    private String duration;

    @Column(length = 200)
    private String instructions;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder = 0;
}
