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

    @Column(name = "medical_record_id", nullable = false)
    private Long medicalRecordId;

    @Column(name = "test_name", nullable = false)
    private String testName;

    @Column(name = "status", nullable = false, length = 50)
    private String status = "ORDERED"; // ORDERED, COMPLETED, CANCELLED

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
