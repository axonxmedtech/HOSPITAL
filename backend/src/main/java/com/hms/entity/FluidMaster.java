package com.hms.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * FluidMaster - Master definitions for fluid intake/output classifications.
 * Reuses the standard master table pattern.
 *
 * @author HMS Team
 * @version Phase-1
 */
@Entity
@Table(name = "fluid_master")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class FluidMaster {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "hospital_id", nullable = false)
    private Long hospitalId;

    @Column(name = "category", nullable = false, length = 30)
    private String category; // e.g. "ORAL", "IV", "TUBE", "BLOOD", "URINE", "DRAIN"

    @Column(name = "name", nullable = false, length = 100)
    private String name; // e.g. "Water", "Normal Saline", "Urine", "Catheter"

    @Column(name = "default_unit", nullable = false, length = 10)
    private String defaultUnit = "ml";
}
