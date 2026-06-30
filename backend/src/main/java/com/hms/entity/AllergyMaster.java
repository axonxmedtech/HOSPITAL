package com.hms.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import java.time.LocalDateTime;

@Entity
@Table(name = "allergy_master")
@Data @NoArgsConstructor @AllArgsConstructor
public class AllergyMaster {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "hospital_id", nullable = false)
    private Long hospitalId;

    @Column(name = "allergy_name", nullable = false, length = 200)
    private String allergyName;

    // DRUG / FOOD / ENVIRONMENTAL / OTHER
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
