package com.hms.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

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

    @Column(name = "medical_record_id")
    private Long medicalRecordId;

    /** Required by the table (a pathology module owns it). Must be set on every insert. */
    @Column(name = "patient_id", nullable = false)
    private Long patientId;

    /** Links the order back to the OPD case, so it can print on the case paper. */
    @Column(name = "opd_id")
    private Long opdId;

    @Column(name = "test_name", nullable = false)
    private String testName;

    @Column(name = "status", nullable = false, length = 50)
    private String status = "ORDERED"; // ORDERED, SAMPLE_COLLECTED (inpatients), COMPLETED, CANCELLED

    /** NOT NULL in the table; ROUTINE unless a stat order is placed. */
    @Column(name = "priority", nullable = false, length = 10)
    private String priority = "ROUTINE";

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    // ── inpatient investigations (V43) ──

    /** The admission an inpatient investigation belongs to; null for an OPD order. */
    @Column(name = "ipd_admission_id")
    private Long ipdAdmissionId;

    @Column(name = "notes", length = 500)
    private String notes;

    @Column(name = "ordered_by")
    private String orderedBy;

    @Column(name = "sample_collected_at")
    private LocalDateTime sampleCollectedAt;

    @Column(name = "sample_collected_by")
    private String sampleCollectedBy;

    @Column(name = "result_text", columnDefinition = "text")
    private String resultText;

    @Column(name = "result_at")
    private LocalDateTime resultAt;

    @Column(name = "result_by")
    private String resultBy;

    @Column(name = "cancel_reason")
    private String cancelReason;

    @Column(name = "cancelled_at")
    private LocalDateTime cancelledAt;
}
