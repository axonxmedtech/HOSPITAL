package com.hms.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import java.time.LocalDateTime;

/**
 * LabResult — One result record per completed LabOrder.
 * Parameters are stored as a JSON TEXT array:
 * [{name, value, unit, referenceRange, flag}, ...]
 * This keeps the schema simple while allowing any number of test parameters.
 */
@Entity
@Table(name = "lab_results")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LabResult {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String publicId;

    @PrePersist
    public void generateIds() {
        if (this.publicId == null) this.publicId = java.util.UUID.randomUUID().toString();
    }

    @Column(name = "hospital_id", nullable = false)
    private Long hospitalId;

    /** One-to-one with LabOrder — unique constraint enforces single result per order */
    @Column(name = "lab_order_id", nullable = false, unique = true)
    private Long labOrderId;

    @Column(name = "patient_id", nullable = false)
    private Long patientId;

    /**
     * JSON array of parameter objects:
     * [{name:"WBC", value:"16000", unit:"/µL", referenceRange:"4000-11000", flag:"High"}, ...]
     */
    @Column(columnDefinition = "text")
    private String parameters;

    /** Plain-text clinical interpretation / overall summary */
    @Column(name = "result_summary", columnDefinition = "text")
    private String resultSummary;

    /** True when any parameter is flagged Low/High/Critical */
    @Column(name = "is_abnormal", nullable = false)
    private Boolean isAbnormal = false;

    /** Optional URL to uploaded PDF/image report */
    @Column(name = "result_file_url", length = 500)
    private String resultFileUrl;

    @Column(name = "resulted_by_name", nullable = false, length = 100)
    private String resultedByName;

    @Column(name = "resulted_at", nullable = false)
    private LocalDateTime resultedAt;

    @Column(name = "verified_by_name", length = 100)
    private String verifiedByName;

    // --- Pathologist sign-off gate (Form 27 BR-4/BR-5/BR-6, all nullable/additive) ---

    @Column(name = "verified_at")
    private LocalDateTime verifiedAt;

    @Column(name = "released_by_name", length = 100)
    private String releasedByName;

    @Column(name = "released_at")
    private LocalDateTime releasedAt;

    // BR-5: technician-flagged critical value (immediate alert), distinct from is_abnormal
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
