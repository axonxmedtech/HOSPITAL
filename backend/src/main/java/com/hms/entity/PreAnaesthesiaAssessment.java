package com.hms.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * Pre-Anaesthesia Assessment / PAC (Form 15 core). One per IPD admission (upserted),
 * performed BEFORE surgery is scheduled. Gates {@code OtService.scheduleBooking}:
 * when a PAC exists, scheduling requires status APPROVED and a passing fitness status
 * (BR-1/BR-3). Additive/nullable columns (Hibernate ddl-auto creates them).
 */
@Entity
@Table(name = "pre_anaesthesia_assessment")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PreAnaesthesiaAssessment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "hospital_id", nullable = false)
    private Long hospitalId;

    @Column(name = "patient_id")
    private Long patientId;

    @Column(name = "admission_id", nullable = false, unique = true)
    private Long admissionId;

    @Column(name = "ot_booking_id")
    private Long otBookingId;

    // ASA I–VI (optionally with E suffix for emergency)
    @Column(name = "asa_class", length = 10)
    private String asaClass;

    // Mallampati grade, mouth opening, neck mobility, dentition notes
    @Column(name = "airway_assessment", columnDefinition = "text")
    private String airwayAssessment;

    // CVS / RS / CNS / renal / hepatic / endocrine findings
    @Column(name = "systemic_assessment", columnDefinition = "text")
    private String systemicAssessment;

    // FIT, FIT_WITH_PRECAUTIONS, FURTHER_EVALUATION, DEFERRED
    @Column(name = "fitness_status", length = 30)
    private String fitnessStatus;

    @Column(name = "planned_anaesthesia", length = 100)
    private String plannedAnaesthesia;

    @Column(name = "remarks", columnDefinition = "text")
    private String remarks;

    // DRAFT or APPROVED
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
