package com.hms.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * Operation Record (Form 18) — the central intra-operative note for a surgery.
 * 1:1 with an {@link OtBooking}. Created after the WHO time-out (booking IN_PROGRESS),
 * finalized after sign-out. Additive/nullable columns (Hibernate ddl-auto creates them).
 */
@Entity
@Table(name = "operation_record")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class OperationRecord {

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

    @Column(name = "procedure_name", length = 200)
    private String procedureName;

    @Column(name = "actual_procedure", columnDefinition = "text")
    private String actualProcedure;

    @Column(name = "operative_findings", columnDefinition = "text")
    private String operativeFindings;

    @Column(name = "estimated_blood_loss", length = 100)
    private String estimatedBloodLoss;

    @Column(name = "complications_summary", columnDefinition = "text")
    private String complicationsSummary;

    @Column(name = "post_op_plan", columnDefinition = "text")
    private String postOpPlan;

    @Column(name = "operation_start")
    private LocalDateTime operationStart;

    @Column(name = "operation_end")
    private LocalDateTime operationEnd;

    // DRAFT or FINALIZED
    @Column(name = "status", length = 20)
    private String status;

    @Column(name = "specimens", columnDefinition = "text")
    private String specimens;

    @Column(name = "implants", columnDefinition = "text")
    private String implants;

    @Column(name = "signed_by", length = 100)
    private String signedBy;

    @Column(name = "signed_at")
    private LocalDateTime signedAt;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
