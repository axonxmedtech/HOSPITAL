package com.hms.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/** Single-use, hashed, expiring OTP for patient portal login (Form 40 phase 1). */
@Entity
@Table(name = "portal_otp")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PortalOtp {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "hospital_id", nullable = false)
    private Long hospitalId;

    @Column(name = "mobile", nullable = false, length = 15)
    private String mobile;

    @Column(name = "otp_hash", nullable = false, length = 100)
    private String otpHash;

    // LOGIN (only value used in this pass)
    @Column(name = "purpose", nullable = false, length = 20)
    private String purpose;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "consumed_at")
    private LocalDateTime consumedAt;

    @Column(name = "attempt_count", nullable = false)
    private Integer attemptCount = 0;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
