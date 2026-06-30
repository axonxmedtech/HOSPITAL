package com.hms.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import java.time.LocalDateTime;

/**
 * RadiologyOrder — Entity for the radiology test orders.
 * Status lifecycle: ORDERED -> STUDY_CONDUCTED -> COMPLETED / CANCELLED
 */
@Entity
@Table(name = "radiology_orders")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RadiologyOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String publicId;

    @PrePersist
    public void generateIds() {
        if (this.publicId == null) {
            this.publicId = java.util.UUID.randomUUID().toString();
        }
    }

    @Column(name = "hospital_id", nullable = false)
    private Long hospitalId;

    /** Nullable — set when order comes from OPD Consultation medical record */
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

    @Column(name = "radiology_test_master_id")
    private Long radiologyTestMasterId;

    /** ROUTINE or URGENT */
    @Column(nullable = false, length = 10)
    private String priority = "ROUTINE";

    /** ORDERED | STUDY_CONDUCTED | COMPLETED | CANCELLED */
    @Column(nullable = false, length = 50)
    private String status = "ORDERED";

    @Column(name = "ordered_by")
    private Long orderedBy;

    @Column(name = "ordered_by_name", length = 100)
    private String orderedByName;

    @Column(columnDefinition = "text")
    private String notes;

    @Column(name = "study_conducted_at")
    private LocalDateTime studyConductedAt;

    @Column(name = "study_conducted_by_name", length = 100)
    private String studyConductedByName;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
