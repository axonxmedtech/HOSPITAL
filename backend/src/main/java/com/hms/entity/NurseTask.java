package com.hms.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Entity
@Table(name = "nurse_tasks")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class NurseTask {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "doctor_order_id")
    private Long doctorOrderId;

    @Column(name = "source", length = 30)
    private String source = "DOCTOR_ORDER";

    @Column(name = "task_type", length = 50)
    private String taskType;

    @Column(name = "missed_reason", columnDefinition = "TEXT")
    private String missedReason;

    @Column(name = "ipd_admission_id", nullable = false)
    private Long ipdAdmissionId;

    @Column(name = "hospital_id", nullable = false)
    private Long hospitalId;

    @Column(name = "scheduled_at")
    private LocalDateTime scheduledAt;

    @Column(name = "executed_at")
    private LocalDateTime executedAt;

    @Column(name = "executed_by")
    private Long executedBy;

    @Column(name = "executed_by_name", length = 100)
    private String executedByName;

    // PENDING, DONE, SKIPPED, REFUSED, HELD
    @Column(nullable = false, length = 20)
    private String status = "PENDING";

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Column(name = "administered_quantity")
    private Double administeredQuantity;

    @Column(length = 50)
    private String route;

    @Column(name = "injection_site", length = 100)
    private String injectionSite;

    @Column(name = "pre_vitals", length = 255)
    private String preVitals;
}
