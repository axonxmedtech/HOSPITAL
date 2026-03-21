package com.hms.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.time.LocalDate;

/**
 * Prescription - Entity to store medicines prescribed during a visit
 * 
 * Each prescription item (medicine) is a row here.
 * Linked to a MedicalRecord.
 * 
 * @author HMS Team
 * @version Phase-3
 */
@Entity
@Table(name = "prescriptions")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Prescription {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "hospital_id", nullable = false)
    private Long hospitalId;

    @Column(name = "medical_record_id", nullable = false)
    private Long medicalRecordId;

    @Column(nullable = false)
    private String medicineName;

    @Column(length = 50)
    private String dosage; // e.g., "500mg"

    @Column(length = 50)
    private String frequency; // e.g., "1-0-1" (Morning-Afternoon-Night)

    @Column(length = 50)
    private String duration; // e.g., "5 Days"

    @Column(name = "duration_days")
    private Integer durationDays; // numeric days where applicable

    @Column(name = "start_date")
    private LocalDate startDate;

    @Column(length = 200)
    private String instructions; // e.g., "After food"

    @Column(nullable = false)
    private String status = "ACTIVE"; // ACTIVE / STOPPED / COMPLETED

    @Column(name = "type", nullable = false)
    private String type = "TABLET"; // TABLET / INJECTION / IV_FLUID

    @Column(name = "route", nullable = false)
    private String route = "ORAL"; // ORAL / IV / IM

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
