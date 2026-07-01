package com.hms.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "vital_signs")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class VitalSigns {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "ipd_admission_id", nullable = false)
    private Long ipdAdmissionId;

    @Column(name = "hospital_id", nullable = false)
    private Long hospitalId;

    @Column(name = "blood_pressure", length = 20)
    private String bloodPressure;

    @Column(name = "bp_systolic")
    private Integer bpSystolic;

    @Column(name = "bp_diastolic")
    private Integer bpDiastolic;

    @Column(name = "pain_score")
    private Integer painScore;

    @Column(name = "weight", precision = 5, scale = 2)
    private java.math.BigDecimal weight;

    @Column(name = "oxygen_support", length = 50)
    private String oxygenSupport;

    @Column(name = "remarks", columnDefinition = "text")
    private String remarks;

    private Integer pulse;

    @Column(precision = 4, scale = 1)
    private BigDecimal temperature;

    private Integer spo2;

    @Column(name = "respiratory_rate")
    private Integer respiratoryRate;

    @Column(name = "temp_method", length = 50)
    private String tempMethod;

    @Column(name = "pulse_rhythm", length = 50)
    private String pulseRhythm;

    @Column(name = "resp_pattern", length = 50)
    private String respPattern;

    @Column(name = "bp_position", length = 50)
    private String bpPosition;

    @Column(name = "recorded_by")
    private Long recordedBy;

    @Column(name = "recorded_by_name", length = 100)
    private String recordedByName;

    @Column(name = "recorded_at")
    private LocalDateTime recordedAt;
}
