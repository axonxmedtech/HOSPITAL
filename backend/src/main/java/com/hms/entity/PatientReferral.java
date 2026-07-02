package com.hms.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Entity
@Table(name = "patient_referral")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PatientReferral {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "public_id", nullable = false, unique = true, length = 50)
    private String publicId;

    @Column(name = "hospital_id", nullable = false)
    private Long hospitalId;

    @Column(name = "patient_id", nullable = false)
    private Long patientId;

    @Column(name = "admission_id", nullable = false)
    private Long admissionId;

    @Column(name = "reassessment_id")
    private Long reassessmentId;

    @Column(name = "specialty", nullable = false, length = 50)
    private String specialty;

    @Column(name = "reason", columnDefinition = "text")
    private String reason;

    // ROUTINE, URGENT, STAT
    @Column(name = "urgency", nullable = false, length = 20)
    private String urgency;

    // REQUESTED, ACCEPTED, COMPLETED
    @Column(name = "status", nullable = false, length = 30)
    private String status = "REQUESTED";

    @Column(name = "requested_by")
    private Long requestedBy;

    @Column(name = "requested_at", nullable = false)
    private LocalDateTime requestedAt;

    @Column(name = "responded_by")
    private Long respondedBy;

    @Column(name = "response_note", columnDefinition = "text")
    private String responseNote;

    @PrePersist
    protected void onCreate() {
        if (this.publicId == null) {
            this.publicId = java.util.UUID.randomUUID().toString();
        }
        if (this.requestedAt == null) {
            this.requestedAt = LocalDateTime.now();
        }
    }
}
