package com.hms.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/** NABH-audited competency certificate (Form 04 core). BR-6: expiry-driven status sweep. */
@Entity
@Table(name = "training_certification")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TrainingCertification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "hospital_id", nullable = false)
    private Long hospitalId;

    @Column(name = "employee_id", nullable = false)
    private Long employeeId;

    @Column(name = "training_master_id", nullable = false)
    private Long trainingMasterId;

    @Column(name = "session_id", nullable = false)
    private Long sessionId;

    @Column(name = "completed_at", nullable = false)
    private LocalDate completedAt;

    @Column(name = "expires_at")
    private LocalDate expiresAt;

    @Column(name = "certificate_ref", length = 100)
    private String certificateRef;

    // VALID / EXPIRING / EXPIRED / REVOKED
    @Column(name = "status", nullable = false, length = 20)
    private String status;
}
