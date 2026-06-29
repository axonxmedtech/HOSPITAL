package com.hms.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "doctor_orders")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DoctorOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String publicId;

    @PrePersist
    public void generatePublicId() {
        if (this.publicId == null) {
            this.publicId = java.util.UUID.randomUUID().toString();
        }
    }

    @Column(name = "ipd_admission_id", nullable = false)
    private Long ipdAdmissionId;

    @Column(name = "hospital_id", nullable = false)
    private Long hospitalId;

    // MEDICATION, INVESTIGATION, PROCEDURE, DIET
    @Column(name = "order_type", nullable = false, length = 20)
    private String orderType;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String description;

    // BD, TDS, QID, OD, SOS, Once
    @Column(length = 20)
    private String frequency;

    @Column(name = "start_date")
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    // ACTIVE, COMPLETED, CANCELLED
    @Column(nullable = false, length = 20)
    private String status = "ACTIVE";

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "created_by_name", length = 100)
    private String createdByName;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(columnDefinition = "TEXT")
    private String notes;
}
