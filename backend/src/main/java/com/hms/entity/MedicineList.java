package com.hms.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Entity
@Table(name = "medicine_list")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MedicineList {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String type; // e.g., Tablet, Syrup, Injection, Saline, Cream

    private String defaultDosage; // e.g., 500mg
    private String defaultFrequency; // e.g., 1-0-1
    private String defaultDuration; // e.g., 3 Days
    private String manufacturer;

    @Column(name = "hospital_id")
    private Long hospitalId; // Null for global catalog, specific hospital ID otherwise

    @Column(name = "is_active")
    private Boolean isActive = true;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
