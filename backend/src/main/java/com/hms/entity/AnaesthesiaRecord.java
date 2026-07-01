package com.hms.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * Anaesthesia Record (Form 19 core / AIMS). 1:1 with an {@link OtBooking}.
 * Starts after the WHO sign-in ("before induction of anaesthesia"), completed + signed
 * at emergence. Completion gates the PACU/recovery record (Form 20). Additive/nullable.
 */
@Entity
@Table(name = "anaesthesia_record")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AnaesthesiaRecord {

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

    @Column(name = "anaesthesiologist_id")
    private Long anaesthesiologistId;

    @Column(name = "anaesthesia_type", length = 100)
    private String anaesthesiaType;      // GENERAL, SPINAL, EPIDURAL, REGIONAL, LOCAL, MAC

    @Column(name = "asa_grade", length = 10)
    private String asaGrade;             // I, II, III, IV, V (E)

    @Column(name = "airway_type", length = 100)
    private String airwayType;           // ETT, LMA, FACE_MASK, TRACHEOSTOMY

    @Column(name = "ventilation_mode", length = 100)
    private String ventilationMode;      // SPONTANEOUS, CONTROLLED, ASSISTED

    @Column(name = "induction_time")
    private LocalDateTime inductionTime;

    @Column(name = "completion_time")
    private LocalDateTime completionTime;

    @Column(name = "notes", columnDefinition = "text")
    private String notes;

    // ACTIVE or COMPLETED
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
