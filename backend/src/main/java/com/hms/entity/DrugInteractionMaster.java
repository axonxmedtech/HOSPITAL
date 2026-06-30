package com.hms.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import java.time.LocalDateTime;

@Entity
@Table(name = "drug_interaction_master")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DrugInteractionMaster {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "hospital_id", nullable = false)
    private Long hospitalId;

    @Column(name = "drug_a_name", nullable = false, length = 200)
    private String drugAName;

    @Column(name = "drug_b_name", nullable = false, length = 200)
    private String drugBName;

    @Column(nullable = false, length = 20)
    private String severity = "MEDIUM";

    @Column(name = "interaction_description", nullable = false, columnDefinition = "text")
    private String interactionDescription;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
