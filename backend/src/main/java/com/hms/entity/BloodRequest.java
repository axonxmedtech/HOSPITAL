package com.hms.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/** Blood Request (Form 38 core) — a ward/OT request for blood component(s). */
@Entity
@Table(name = "blood_request")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class BloodRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "hospital_id", nullable = false)
    private Long hospitalId;

    @Column(name = "patient_id", nullable = false)
    private Long patientId;

    @Column(name = "admission_id")
    private Long admissionId;

    @Column(name = "department", nullable = false, length = 50)
    private String department;

    @Column(name = "component", nullable = false, length = 20)
    private String component;

    @Column(name = "units_requested", nullable = false)
    private Integer unitsRequested;

    @Column(name = "priority", nullable = false, length = 20)
    private String priority; // ROUTINE / URGENT / STAT

    // PENDING / CROSS_MATCHING / FILLED / CANCELLED
    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @Column(name = "requested_by_name", length = 100)
    private String requestedByName;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
