package com.hms.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * Facility maintenance helpdesk ticket (Form 37 core). BR-4: the ticket only closes once
 * both the resolving engineer and the reporting nurse have confirmed the fix.
 */
@Entity
@Table(name = "facility_complaint")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class FacilityComplaint {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "hospital_id", nullable = false)
    private Long hospitalId;

    @Column(name = "location", nullable = false, length = 50)
    private String location;

    // LEAKAGE / LIGHTING / ELECTRICAL / AC / PLUMBING
    @Column(name = "complaint_type", nullable = false, length = 30)
    private String complaintType;

    @Column(name = "reported_by", nullable = false, length = 100)
    private String reportedBy;

    @Column(name = "assigned_to", length = 100)
    private String assignedTo;

    // OPEN / IN_PROGRESS / RESOLVED / CLOSED
    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @Column(name = "engineer_confirmed", nullable = false)
    private boolean engineerConfirmed;

    @Column(name = "nurse_confirmed", nullable = false)
    private boolean nurseConfirmed;

    @Column(name = "resolution", columnDefinition = "TEXT")
    private String resolution;

    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
