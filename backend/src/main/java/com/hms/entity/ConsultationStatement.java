package com.hms.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

/**
 * ConsultationStatement - Standard bilingual patient instructions and doctor advice.
 *
 * Categorized by:
 * - MEDICINE_INSTRUCTION (e.g. "Take with water", "At bedtime", "In the morning")
 * - DOCTOR_ADVICE (e.g. "Drink more water.", "Avoid oily and spicy food.", "Take adequate rest.")
 *
 * Partitioned by hospitalType ("HOSPITAL", "CLINIC", "PHARMACY", or "ALL").
 */
@Entity
@Table(name = "consultation_statements")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ConsultationStatement {

    public static final String CATEGORY_MEDICINE_INSTRUCTION = "MEDICINE_INSTRUCTION";
    public static final String CATEGORY_DOCTOR_ADVICE = "DOCTOR_ADVICE";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Target type: "HOSPITAL", "CLINIC", "PHARMACY", or "ALL"
     */
    @Column(name = "hospital_type", nullable = false, length = 20)
    private String hospitalType = "ALL";

    @Column(name = "category", nullable = false, length = 30)
    private String category;

    @Column(name = "english_text", nullable = false, length = 500)
    private String englishText;

    @Column(name = "marathi_text", nullable = false, length = 500)
    private String marathiText;

    @Column(name = "hindi_text", nullable = false, length = 500)
    private String hindiText;

    @Column(name = "display_order", nullable = false)
    private Integer displayOrder = 0;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}
