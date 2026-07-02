package com.hms.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * Feedback Token (Form 03 auth spine). An unguessable single-use token issued for a
 * completed encounter; the public feedback form resolves patient/hospital server-side
 * from this token so the public payload never carries tenant/patient IDs (BR-7).
 */
@Entity
@Table(name = "feedback_token")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class FeedbackToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "token", nullable = false, unique = true, length = 64)
    private String token;

    @Column(name = "hospital_id", nullable = false)
    private Long hospitalId;

    @Column(name = "patient_id", nullable = false)
    private Long patientId;

    @Column(name = "appointment_id")
    private Long appointmentId;

    @Column(name = "admission_id")
    private Long admissionId;

    @Column(name = "feedback_type", nullable = false, length = 10)
    private String feedbackType; // OPD / IPD

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "used_at")
    private LocalDateTime usedAt;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
