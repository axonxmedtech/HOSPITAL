package com.hms.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * Bed/room cleaning task (Form 37 core). BR-1: a supervisor-verified COMPLETED task is what
 * releases the location back to use (e.g. bed turnover).
 */
@Entity
@Table(name = "cleaning_task")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CleaningTask {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "hospital_id", nullable = false)
    private Long hospitalId;

    @Column(name = "location", nullable = false, length = 50)
    private String location;

    // ROUTINE / DEEP / TERMINAL / EMERGENCY
    @Column(name = "task_type", nullable = false, length = 30)
    private String taskType;

    @Column(name = "assigned_to", length = 100)
    private String assignedTo;

    // ROUTINE / URGENT
    @Column(name = "priority", nullable = false, length = 20)
    private String priority;

    // DIRTY / IN_PROGRESS / PENDING_VERIFICATION / COMPLETED
    @Column(name = "status", nullable = false, length = 30)
    private String status;

    @Column(name = "start_time")
    private LocalDateTime startTime;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "supervisor_sig", columnDefinition = "TEXT")
    private String supervisorSig;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
