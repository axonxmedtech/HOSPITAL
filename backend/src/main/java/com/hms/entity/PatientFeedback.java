package com.hms.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * Patient Feedback (Form 03 core). Immutable after submission (BR-5) — only status and
 * review fields may change thereafter. BR-6: doctors/nurses/reception have zero read
 * access to this entity (enforced at the controller's @PreAuthorize).
 */
@Entity
@Table(name = "patient_feedback")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PatientFeedback {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "hospital_id", nullable = false)
    private Long hospitalId;

    @Column(name = "patient_id", nullable = false)
    private Long patientId;

    @Column(name = "appointment_id")
    private Long appointmentId;

    @Column(name = "admission_id")
    private Long admissionId;

    @Column(name = "feedback_type", nullable = false, length = 10)
    private String feedbackType;

    @Column(name = "submitted_by", length = 20)
    private String submittedBy; // PATIENT / RELATIVE

    @Column(name = "source", length = 30)
    private String source;

    @Column(name = "overall_rating", nullable = false)
    private Integer overallRating;

    @Column(name = "reception_rating")
    private Integer receptionRating;

    @Column(name = "doctor_rating")
    private Integer doctorRating;

    @Column(name = "nurse_rating")
    private Integer nurseRating;

    @Column(name = "housekeeping_rating")
    private Integer housekeepingRating;

    @Column(name = "billing_rating")
    private Integer billingRating;

    @Column(name = "facility_rating", length = 20)
    private String facilityRating; // YES / NO / PARTIALLY

    @Column(name = "recommend_score")
    private Integer recommendScore; // NPS 0-10

    @Column(name = "complaints", columnDefinition = "text")
    private String complaints;

    @Column(name = "suggestions", columnDefinition = "text")
    private String suggestions;

    // PENDING / SUBMITTED / UNDER_REVIEW / ACTIONED / CLOSED
    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @Column(name = "reviewed_by", length = 100)
    private String reviewedBy;

    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    @Column(name = "submitted_at")
    private LocalDateTime submittedAt;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
