package com.hms.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * Patient portal login identity (Form 40 phase 1) — one row per (hospital, patient),
 * created lazily on first OTP request. Deliberately separate from the staff {@link User}
 * table: patients authenticate via OTP only and have no password.
 */
@Entity
@Table(name = "patient_portal_user")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PatientPortalUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "hospital_id", nullable = false)
    private Long hospitalId;

    @Column(name = "patient_id", nullable = false)
    private Long patientId;

    @Column(name = "mobile", nullable = false, length = 15)
    private String mobile;

    @Column(name = "email", length = 100)
    private String email;

    // ACTIVE / LOCKED / SUSPENDED
    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @Column(name = "lock_until")
    private LocalDateTime lockUntil;

    @Column(name = "last_login")
    private LocalDateTime lastLogin;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
