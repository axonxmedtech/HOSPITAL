package com.hms.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import java.time.LocalDateTime;

/**
 * RadiologyResult — Stores report findings, clinical impression, abnormal flag, and image link.
 */
@Entity
@Table(name = "radiology_results")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RadiologyResult {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String publicId;

    @PrePersist
    public void generateIds() {
        if (this.publicId == null) {
            this.publicId = java.util.UUID.randomUUID().toString();
        }
    }

    @Column(name = "hospital_id", nullable = false)
    private Long hospitalId;

    @Column(name = "radiology_order_id", nullable = false, unique = true)
    private Long radiologyOrderId;

    @Column(name = "patient_id", nullable = false)
    private Long patientId;

    @Column(columnDefinition = "text")
    private String findings;

    @Column(columnDefinition = "text")
    private String impression;

    @Column(name = "is_abnormal", nullable = false)
    private Boolean isAbnormal = false;

    @Column(name = "result_file_url", length = 500)
    private String resultFileUrl;

    @Column(name = "resulted_by_name", nullable = false, length = 100)
    private String resultedByName;

    @Column(name = "resulted_at", nullable = false)
    private LocalDateTime resultedAt;

    @Column(name = "verified_by_name", length = 100)
    private String verifiedByName;

    // --- Radiologist sign-off gate (Form 28 BR-4/BR-5/BR-6, all nullable/additive) ---

    @Column(name = "verified_at")
    private LocalDateTime verifiedAt;

    @Column(name = "released_by_name", length = 100)
    private String releasedByName;

    @Column(name = "released_at")
    private LocalDateTime releasedAt;

    // BR-5: radiologist-flagged critical finding (e.g. intracranial haemorrhage, pneumothorax)
    @Column(name = "is_critical", nullable = false)
    private Boolean isCritical = false;

    @Column(name = "critical_alert_sent_at")
    private LocalDateTime criticalAlertSentAt;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public Boolean getIsAbnormal() { return isAbnormal; }
    public void setIsAbnormal(Boolean isAbnormal) { this.isAbnormal = isAbnormal; }
}
