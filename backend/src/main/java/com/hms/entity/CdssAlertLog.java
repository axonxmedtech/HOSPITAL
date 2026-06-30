package com.hms.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import java.time.LocalDateTime;

@Entity
@Table(name = "cdss_alert_log")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CdssAlertLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "hospital_id", nullable = false)
    private Long hospitalId;

    @Column(name = "alert_type", nullable = false, length = 50)
    private String alertType;

    @Column(name = "patient_id")
    private Long patientId;

    @Column(name = "ipd_admission_id")
    private Long ipdAdmissionId;

    @Column(name = "alert_message", nullable = false, columnDefinition = "text")
    private String alertMessage;

    @Column(nullable = false, length = 20)
    private String severity;

    @Column(name = "acknowledged_by_user_id")
    private Long acknowledgedByUserId;

    @Column(name = "acknowledged_at")
    private LocalDateTime acknowledgedAt;

    @Column(name = "override_reason", length = 500)
    private String overrideReason;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
