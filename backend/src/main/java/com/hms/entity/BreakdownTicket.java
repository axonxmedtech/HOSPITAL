package com.hms.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Breakdown/repair ticket (Form 36 core). BR-4: the device stays DOWN and the ticket stays
 * open until the reporting department confirms the repair via close().
 */
@Entity
@Table(name = "breakdown_ticket")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class BreakdownTicket {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "hospital_id", nullable = false)
    private Long hospitalId;

    @Column(name = "equipment_id", nullable = false)
    private Long equipmentId;

    @Column(name = "reported_by", nullable = false, length = 100)
    private String reportedBy;

    @Column(name = "reported_at", nullable = false)
    private LocalDateTime reportedAt;

    // LOW / MEDIUM / CRITICAL
    @Column(name = "priority", nullable = false, length = 20)
    private String priority;

    @Column(name = "remarks", columnDefinition = "TEXT")
    private String remarks;

    // OPEN / IN_PROGRESS / REPAIRED / CLOSED
    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @Column(name = "engineer_sig", columnDefinition = "TEXT")
    private String engineerSig;

    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;
}
