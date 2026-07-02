package com.hms.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/** Quality Complaint (Form 03 BR-4) — auto-created when feedback indicates a problem. */
@Entity
@Table(name = "quality_complaint")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class QualityComplaint {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "hospital_id", nullable = false)
    private Long hospitalId;

    @Column(name = "feedback_id", nullable = false)
    private Long feedbackId;

    @Column(name = "category", length = 30)
    private String category;

    @Column(name = "department", length = 50)
    private String department;

    @Column(name = "description", columnDefinition = "text")
    private String description;

    // LOW / MEDIUM / HIGH
    @Column(name = "severity", nullable = false, length = 10)
    private String severity;

    @Column(name = "assigned_to_name", length = 100)
    private String assignedToName;

    // OPEN / INVESTIGATING / ACTION_TAKEN / CLOSED
    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @Column(name = "resolution", columnDefinition = "text")
    private String resolution;

    @Column(name = "resolved_by_name", length = 100)
    private String resolvedByName;

    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
