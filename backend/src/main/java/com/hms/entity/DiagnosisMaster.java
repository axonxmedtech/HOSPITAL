package com.hms.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import java.time.LocalDateTime;

@Entity
@Table(name = "diagnosis_master")
@Data @NoArgsConstructor @AllArgsConstructor
public class DiagnosisMaster {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "hospital_id", nullable = false)
    private Long hospitalId;

    @Column(name = "icd_code", nullable = false, length = 20)
    private String icdCode;

    @Column(name = "icd_description", nullable = false, length = 500)
    private String icdDescription;

    // INFECTIOUS / CARDIOVASCULAR / RESPIRATORY / ENDOCRINE / NEUROLOGICAL /
    // MUSCULOSKELETAL / GASTROINTESTINAL / GENITOURINARY / OBSTETRIC /
    // MENTAL / INJURY / NEOPLASM / OTHER
    @Column(length = 50, nullable = false)
    private String category = "OTHER";

    @Column(name = "is_custom", nullable = false)
    private Boolean isCustom = false;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
