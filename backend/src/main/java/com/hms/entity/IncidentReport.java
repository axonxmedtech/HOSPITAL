package com.hms.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "incident_reports")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class IncidentReport {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "hospital_id", nullable = false)
    private Long hospitalId;

    @Column(name = "patient_id")
    private Long patientId;

    @Column(name = "admission_id")
    private Long admissionId;

    // OT_COMPLICATION, OT_INSTRUMENT_COUNT, CSSD_STERILIZATION, etc.
    @Column(name = "source", nullable = false, length = 50)
    private String source;

    @Column(name = "related_entity_id")
    private Long relatedEntityId;

    // LOW, MEDIUM, HIGH
    @Column(name = "severity", length = 20)
    private String severity;

    @Column(name = "description", columnDefinition = "text")
    private String description;

    // PENDING_REVIEW, INVESTIGATING, CLOSED
    @Column(name = "status", nullable = false, length = 30)
    private String status = "PENDING_REVIEW";

    @Column(name = "reported_by", length = 100)
    private String reportedBy;

    @CreationTimestamp
    @Column(name = "reported_at", nullable = false, updatable = false)
    private LocalDateTime reportedAt;
}
