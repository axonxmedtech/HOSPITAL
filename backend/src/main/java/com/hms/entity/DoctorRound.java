package com.hms.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import java.time.LocalDateTime;

@Entity
@Table(name = "doctor_rounds")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DoctorRound {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "ipd_admission_id", nullable = false)
    private Long ipdAdmissionId;

    @Column(name = "doctor_id", nullable = false)
    private Long doctorId;

    @Column(name = "hospital_id", nullable = false)
    private Long hospitalId;

    @Column(name = "round_date_time", nullable = false)
    private LocalDateTime roundDateTime;

    @Column(columnDefinition = "TEXT")
    private String subjective;

    @Column(columnDefinition = "TEXT")
    private String objective;

    @Column(columnDefinition = "TEXT")
    private String assessment;

    @Column(columnDefinition = "TEXT")
    private String plan;

    @Column(name = "next_round_at")
    private LocalDateTime nextRoundAt;

    @Column(name = "doctor_name", nullable = false, length = 100)
    private String doctorName;

    // --- Clinical Documentation Engine (Forms 11/13, all nullable/additive) ---

    // PROGRESS_NOTE (Form 13, default) or REASSESSMENT (Form 11)
    @Column(name = "assessment_type", length = 30)
    private String assessmentType;

    // SIGNED (on creation) or AMENDED (superseded by a correction). Null on legacy rows = implicitly SIGNED.
    @Column(name = "status", length = 20)
    private String status;

    @Column(name = "signed_by", length = 100)
    private String signedBy;

    @Column(name = "signed_at")
    private LocalDateTime signedAt;

    // Amendment chain: the id of the note this one corrects (originals are never mutated).
    @Column(name = "amended_from_id")
    private Long amendedFromId;

    @Column(name = "amendment_reason", length = 255)
    private String amendmentReason;

    @Column(name = "clinical_status", columnDefinition = "text")
    private String clinicalStatus;

    @Column(name = "clinical_impression", length = 30)
    private String clinicalImpression;

    @Column(name = "baseline_assessment_id")
    private Long baselineAssessmentId;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
