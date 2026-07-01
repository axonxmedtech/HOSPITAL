package com.hms.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

/**
 * PatientRiskAssessment - Entity representing clinical risk screening (Fall, Pressure Ulcer, Nutrition).
 * Drives notification alarms and auto-escalations (Clinical Risk Engine).
 *
 * @author HMS Team
 * @version Phase-1
 */
@Entity
@Table(name = "patient_risk_assessment")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PatientRiskAssessment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "public_id", nullable = false, unique = true, length = 36)
    private String publicId;

    @Column(name = "hospital_id", nullable = false)
    private Long hospitalId;

    @Column(name = "patient_id", nullable = false)
    private Long patientId;

    @Column(name = "admission_id", nullable = false)
    private Long admissionId;

    @Column(name = "scale_type", nullable = false, length = 30)
    private String scaleType = "VULNERABILITY";

    @Column(name = "fall_risk", nullable = false, length = 10)
    private String fallRisk = "LOW"; // LOW / MED / HIGH

    @Column(name = "pressure_ulcer_risk", nullable = false, length = 10)
    private String pressureUlcerRisk = "LOW";

    @Column(name = "nutrition_risk", nullable = false, length = 10)
    private String nutritionRisk = "LOW";

    @Column(name = "mental_status", length = 50)
    private String mentalStatus;

    @Column(name = "mobility_status", length = 50)
    private String mobilityStatus;

    @Column(name = "infection_risk", nullable = false)
    private Boolean infectionRisk = false;

    @Column(name = "allergy_risk", nullable = false)
    private Boolean allergyRisk = false;

    @Column(name = "isolation_required", nullable = false)
    private Boolean isolationRequired = false;

    @Column(name = "overall_risk", nullable = false, length = 10)
    private String overallRisk = "LOW";

    @Column(name = "inputs_json", columnDefinition = "text")
    private String inputsJson;

    @Column(name = "status", nullable = false, length = 20)
    private String status = "DRAFT"; // DRAFT / LOCKED

    @Column(name = "version", nullable = false)
    private Integer version = 1;

    @Column(name = "parent_id")
    private Long parentId;

    @Column(name = "assessed_by", nullable = false)
    private Long assessedBy;

    @Column(name = "reviewed_by")
    private Long reviewedBy;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        if (this.publicId == null) {
            this.publicId = java.util.UUID.randomUUID().toString();
        }
    }
}
