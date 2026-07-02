package com.hms.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Blood Unit (Form 38 core) — one component bag in inventory, traceable to its donor.
 * BR-3: units past expiry are auto-quarantined and cannot be issued.
 */
@Entity
@Table(name = "blood_unit")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class BloodUnit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "hospital_id", nullable = false)
    private Long hospitalId;

    @Column(name = "unit_number", nullable = false, unique = true, length = 30)
    private String unitNumber;

    @Column(name = "donor_id", nullable = false)
    private Long donorId;

    @Column(name = "component_type", nullable = false, length = 20)
    private String componentType; // WHOLE_BLOOD, PRBC, FFP, PLATELETS

    @Column(name = "blood_group", nullable = false, length = 5)
    private String bloodGroup;

    @Column(name = "rh_type", nullable = false, length = 10)
    private String rhType;

    @Column(name = "hiv_result", length = 20)
    private String hivResult;

    @Column(name = "hbsag_result", length = 20)
    private String hbsagResult;

    @Column(name = "malaria_result", length = 20)
    private String malariaResult;

    // AVAILABLE / RESERVED / ISSUED / EXPIRED / QUARANTINED
    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @Column(name = "expiry_date", nullable = false)
    private LocalDate expiryDate;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
