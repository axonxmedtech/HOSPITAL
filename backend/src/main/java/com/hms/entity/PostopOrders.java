package com.hms.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * Post-operative Orders (Form 21 core) — the attending surgeon's post-op instruction bundle.
 * 1:1 with an {@link OtBooking}. Draft while being written; once SIGNED it is immutable (BR-6).
 * Medications / monitoring / investigations are captured as text in this first increment
 * (structured child tables + Pharmacy/MAR/DoctorOrder fan-out are deferred). Additive/nullable.
 */
@Entity
@Table(name = "postoperative_orders")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PostopOrders {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "hospital_id", nullable = false)
    private Long hospitalId;

    @Column(name = "patient_id")
    private Long patientId;

    @Column(name = "admission_id")
    private Long admissionId;

    @Column(name = "ot_booking_id", nullable = false, unique = true)
    private Long otBookingId;

    @Column(name = "surgeon_id")
    private Long surgeonId;

    @Column(name = "postop_diagnosis", length = 250)
    private String postopDiagnosis;

    // Named post_condition to avoid the SQL reserved word `condition`.
    @Column(name = "post_condition", length = 30)
    private String condition;

    @Column(name = "diet_order", length = 30)
    private String dietOrder;

    @Column(name = "activity_order", length = 30)
    private String activityOrder;

    @Column(name = "medications", columnDefinition = "text")
    private String medications;

    @Column(name = "monitoring_plan", columnDefinition = "text")
    private String monitoringPlan;

    @Column(name = "investigations", columnDefinition = "text")
    private String investigations;

    @Column(name = "escalation_instructions", columnDefinition = "text")
    private String escalationInstructions;

    // DRAFT or SIGNED
    @Column(name = "status", length = 20)
    private String status;

    @Column(name = "signed_by", length = 100)
    private String signedBy;

    @Column(name = "signed_at")
    private LocalDateTime signedAt;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
