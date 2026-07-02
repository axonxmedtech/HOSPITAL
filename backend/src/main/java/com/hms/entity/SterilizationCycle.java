package com.hms.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Autoclave run record (Form 35 core). BR-2: chemical/biological FAIL fails the whole load
 * and quarantines every mapped tray. BR-5: every state change is ledgered via AuditLog.
 */
@Entity
@Table(name = "sterilization_cycle")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SterilizationCycle {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "hospital_id", nullable = false)
    private Long hospitalId;

    @Column(name = "cycle_number", nullable = false, length = 30)
    private String cycleNumber;

    @Column(name = "machine_id", nullable = false, length = 20)
    private String machineId;

    // STEAM / ETO / PLASMA / DRY_HEAT
    @Column(name = "method", nullable = false, length = 20)
    private String method;

    @Column(name = "temperature", nullable = false, precision = 4, scale = 1)
    private BigDecimal temperature;

    @Column(name = "pressure", nullable = false, precision = 4, scale = 2)
    private BigDecimal pressure;

    @Column(name = "duration", nullable = false)
    private Integer duration;

    // PASS / FAIL, set at verification
    @Column(name = "chemical_result", length = 10)
    private String chemicalResult;

    @Column(name = "biological_result", length = 10)
    private String biologicalResult;

    // IN_PROGRESS / PASSED / FAILED
    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @Column(name = "approved_by")
    private Long approvedBy;

    @Column(name = "approved_by_sig", columnDefinition = "TEXT")
    private String approvedBySig;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
