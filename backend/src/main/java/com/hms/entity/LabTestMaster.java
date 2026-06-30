package com.hms.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "lab_test_master")
@Data @NoArgsConstructor @AllArgsConstructor
public class LabTestMaster {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "hospital_id", nullable = false)
    private Long hospitalId;

    @Column(name = "test_code", length = 50)
    private String testCode;

    @Column(name = "test_name", nullable = false, length = 200)
    private String testName;

    // BIOCHEMISTRY / HEMATOLOGY / MICROBIOLOGY / SEROLOGY / PATHOLOGY / OTHER
    @Column(length = 50, nullable = false)
    private String department = "OTHER";

    // BLOOD / URINE / STOOL / SWAB / CSF / OTHER
    @Column(name = "sample_type", length = 50, nullable = false)
    private String sampleType = "BLOOD";

    @Column(name = "normal_range_text", length = 500)
    private String normalRangeText;

    @Column(length = 50)
    private String unit;

    @Column(name = "turnaround_hours")
    private Integer turnaroundHours;

    @Column(precision = 10, scale = 2)
    private BigDecimal price;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
