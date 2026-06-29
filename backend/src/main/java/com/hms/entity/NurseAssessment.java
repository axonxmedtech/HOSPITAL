package com.hms.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "nurse_assessments")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class NurseAssessment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "ipd_admission_id", nullable = false, unique = true)
    private Long ipdAdmissionId;

    @Column(name = "hospital_id", nullable = false)
    private Long hospitalId;

    @Column(name = "blood_pressure", length = 20)
    private String bloodPressure;

    private Integer pulse;

    @Column(precision = 4, scale = 1)
    private BigDecimal temperature;

    private Integer spo2;

    @Column(precision = 5, scale = 1)
    private BigDecimal height;

    @Column(precision = 5, scale = 1)
    private BigDecimal weight;

    @Column(name = "pain_score")
    private Integer painScore;

    @Column(columnDefinition = "TEXT")
    private String allergies;

    @Column(name = "fall_risk", length = 10)
    private String fallRisk;

    @Column(name = "general_condition", columnDefinition = "TEXT")
    private String generalCondition;

    @Column(name = "chief_complaint_on_admission", columnDefinition = "TEXT")
    private String chiefComplaintOnAdmission;

    @Column(name = "assessed_by")
    private Long assessedBy;

    @Column(name = "assessed_by_name", length = 100)
    private String assessedByName;

    @Column(name = "assessed_at")
    private LocalDateTime assessedAt;
}
