package com.hms.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * MonitoringVitals - Shared JPA entity for intraoperative (OT) and PACU recovery vitals monitoring.
 * Disambiguated by a context discriminator field.
 *
 * @author HMS Team
 * @version Phase-0.7
 */
@Entity
@Table(name = "monitoring_vitals")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MonitoringVitals {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "ipd_admission_id", nullable = false)
    private Long ipdAdmissionId;

    @Column(name = "hospital_id", nullable = false)
    private Long hospitalId;

    @Column(name = "context", nullable = false, length = 20)
    private String context; // "INTRAOP" or "PACU"

    @Column(name = "pulse")
    private Integer pulse;

    @Column(name = "bp_systolic")
    private Integer bpSystolic;

    @Column(name = "bp_diastolic")
    private Integer bpDiastolic;

    @Column(name = "spo2")
    private Integer spo2;

    @Column(name = "respiratory_rate")
    private Integer respiratoryRate;

    @Column(name = "temperature", precision = 4, scale = 1)
    private BigDecimal temperature;

    @Column(name = "recorded_by")
    private Long recordedBy;

    @Column(name = "recorded_by_name", length = 100)
    private String recordedByName;

    @Column(name = "recorded_at", nullable = false)
    private LocalDateTime recordedAt;
}
