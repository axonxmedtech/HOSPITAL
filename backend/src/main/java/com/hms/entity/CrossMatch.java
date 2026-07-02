package com.hms.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * Cross-Match (Form 38 core, BR-4) — patient/bag compatibility check. A blood unit can only
 * be issued once a COMPATIBLE cross-match exists for the requesting patient.
 */
@Entity
@Table(name = "cross_match")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CrossMatch {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "hospital_id", nullable = false)
    private Long hospitalId;

    @Column(name = "request_id", nullable = false)
    private Long requestId;

    @Column(name = "blood_unit_id", nullable = false)
    private Long bloodUnitId;

    @Column(name = "patient_id", nullable = false)
    private Long patientId;

    // COMPATIBLE / INCOMPATIBLE
    @Column(name = "result", nullable = false, length = 20)
    private String result;

    @Column(name = "verified_by_name", length = 100)
    private String verifiedByName;

    @Column(name = "verified_at")
    private LocalDateTime verifiedAt;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
