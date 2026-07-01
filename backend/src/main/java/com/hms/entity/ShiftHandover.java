package com.hms.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

/**
 * ShiftHandover - Entity logging continuity handover details between outgoing and incoming nurses.
 *
 * @author HMS Team
 * @version Phase-1
 */
@Entity
@Table(name = "shift_handover")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ShiftHandover {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "hospital_id", nullable = false)
    private Long hospitalId;

    @Column(name = "admission_id", nullable = false)
    private Long admissionId;

    @Column(name = "shift", nullable = false, length = 20)
    private String shift; // MORNING / EVENING / NIGHT

    @Column(name = "outgoing_nurse_id", nullable = false)
    private Long outgoingNurseId;

    @Column(name = "incoming_nurse_id", nullable = false)
    private Long incomingNurseId;

    @Column(name = "pending_tasks", columnDefinition = "text")
    private String pendingTasks;

    @Column(name = "critical_alerts", columnDefinition = "text")
    private String criticalAlerts;

    @Column(name = "meds_due", columnDefinition = "text")
    private String medsDue;

    @Column(name = "investigations_pending", columnDefinition = "text")
    private String investigationsPending;

    @Column(name = "doctor_review_pending", nullable = false)
    private Boolean doctorReviewPending = false;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}
