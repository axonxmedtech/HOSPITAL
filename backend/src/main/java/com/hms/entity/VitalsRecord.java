package com.hms.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * VitalsRecord - a single timestamped set of vitals for an IPD admission
 * (Phase 1 Nurse module). Time-series: many rows per admission. Kept separate
 * from the OPD vitals snapshot (opd.bp/temperature/...) which is untouched.
 * BP is stored as two integers to enable trend charts.
 */
@Entity
@Table(name = "vitals_records")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class VitalsRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String publicId;

    @Column(name = "hospital_id", nullable = false)
    private Long hospitalId;

    @Column(name = "ipd_admission_id", nullable = false)
    private Long ipdAdmissionId;

    @Column(name = "patient_id", nullable = false)
    private Long patientId;

    @Column(name = "recorded_by_user_id", nullable = false)
    private Long recordedByUserId;

    @Column(name = "performed_by_nurse_id")
    private Long performedByNurseId;

    @Column(name = "recorded_at", nullable = false)
    private LocalDateTime recordedAt;

    // Vitals have no upper limit (only >= 0), so these decimals must be wide enough to hold
    // whatever is entered. The old precisions (4,1 / 5,2) were sized for the removed caps
    // (temp <= 113, weight <= 500) and overflowed with "Data truncation: Out of range value".
    @Column(name = "temperature", precision = 12, scale = 1)
    private BigDecimal temperature;

    @Column(name = "pulse")
    private Integer pulse;

    @Column(name = "bp_systolic")
    private Integer bpSystolic;

    @Column(name = "bp_diastolic")
    private Integer bpDiastolic;

    @Column(name = "respiratory_rate")
    private Integer respiratoryRate;

    @Column(name = "spo2")
    private Integer spo2;

    @Column(name = "weight", precision = 12, scale = 2)
    private BigDecimal weight;

    @Column(name = "pain_score")
    private Integer painScore;

    @Column(name = "remarks", length = 500)
    private String remarks;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    public void prePersist() {
        if (this.publicId == null) {
            this.publicId = java.util.UUID.randomUUID().toString();
        }
        if (this.recordedAt == null) {
            this.recordedAt = LocalDateTime.now();
        }
    }
}
