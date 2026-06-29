package com.hms.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import java.time.LocalDateTime;

/**
 * LabOrder — Extended entity for the full lab workflow.
 * Status machine: ORDERED → SAMPLE_COLLECTED → COMPLETED / CANCELLED
 * Previously only had: id, publicId, hospitalId, medicalRecordId, testName, status, createdAt.
 * Now includes: patient/IPD/OPD context, priority, ordered-by info, sample tracking, updatedAt.
 */
@Entity
@Table(name = "lab_orders")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LabOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String publicId;

    @PrePersist
    public void generateIds() {
        if (this.publicId == null) this.publicId = java.util.UUID.randomUUID().toString();
    }

    @Column(name = "hospital_id", nullable = false)
    private Long hospitalId;

    /** Nullable — only set when order comes from OPD medical record flow */
    @Column(name = "medical_record_id")
    private Long medicalRecordId;

    @Column(name = "patient_id", nullable = false)
    private Long patientId;

    @Column(name = "ipd_admission_id")
    private Long ipdAdmissionId;

    @Column(name = "opd_id")
    private Long opdId;

    @Column(name = "test_name", nullable = false)
    private String testName;

    /** ROUTINE or URGENT */
    @Column(nullable = false, length = 10)
    private String priority = "ROUTINE";

    /** ORDERED | SAMPLE_COLLECTED | COMPLETED | CANCELLED */
    @Column(nullable = false, length = 50)
    private String status = "ORDERED";

    @Column(name = "ordered_by")
    private Long orderedBy;

    @Column(name = "ordered_by_name", length = 100)
    private String orderedByName;

    @Column(columnDefinition = "text")
    private String notes;

    @Column(name = "sample_collected_at")
    private LocalDateTime sampleCollectedAt;

    @Column(name = "sample_collected_by_name", length = 100)
    private String sampleCollectedByName;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
