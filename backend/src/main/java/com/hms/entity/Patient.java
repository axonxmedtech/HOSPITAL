package com.hms.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * Patient - Entity representing a patient in a hospital
 * 
 * This entity stores patient information for OPD management.
 * Each patient belongs to exactly one hospital (multi-tenant isolation via
 * hospital_id).
 * Only Hospital Admin can add patients.
 * 
 * @author HMS Team
 * @version Phase-1
 */
@Entity
@Table(name = "patients")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Patient {

    /**
     * Unique identifier for the patient
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Public unique identifier (UUID) for security
     */
    /**
     * Public unique identifier (UUID) for security
     */
    @Column(nullable = false, unique = true)
    private String publicId;

    /**
     * Custom readable ID for UI display (e.g., PAT1234)
     */
    @Column(name = "custom_id")
    private String customId;

    @PrePersist
    public void generateIds() {
        if (this.publicId == null) {
            this.publicId = java.util.UUID.randomUUID().toString();
        }
        // customId is set by PatientService after save using the auto-increment id
    }

    /**
     * Hospital ID for multi-tenant isolation
     * Every patient belongs to exactly one hospital
     */
    @Column(name = "hospital_id", nullable = false)
    private Long hospitalId;

    /**
     * Patient's full name
     */
    @Column(nullable = false, length = 100)
    private String name;

    /**
     * Patient's age
     */
    @Column(nullable = false)
    private Integer age;

    /**
     * Patient's gender (MALE, FEMALE, OTHER)
     */
    @Column(nullable = false, length = 10)
    private String gender;

    /**
     * Patient's contact phone number
     */
    @Column(nullable = false, length = 15)
    @jakarta.validation.constraints.NotBlank(message = "Phone number is required")
    @jakarta.validation.constraints.Pattern(regexp = "^[0-9]{10}$", message = "Phone number must be exactly 10 digits")
    private String phone;

    /**
     * Patient's email address (optional)
     */
    @Column(length = 100)
    private String email;

    /**
     * Patient's address
     */
    @Column(length = 255)
    private String address;

    /**
     * Patient consultation status
     * REGISTERED - Initial state when patient is added
     * CONSULTING - Doctor has started consultation
     * COMPLETED - Consultation finished, prescription given
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private PatientStatus status = PatientStatus.REGISTERED;

    /**
     * CMS: Medical History / Allergies / Notes
     * Simplied text field for Phase 1
     */
    @Column(name = "medical_history", length = 1000)
    private String medicalHistory;

    /**
     * Soft delete flag
     */
    @Column(nullable = false)
    private Boolean isActive = true;

    /**
     * Timestamp when the patient record was created
     */
    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /**
     * Transient field to hold the latest bill information for UI display.
     * This is not persisted in the patient table.
     */
    @Transient
    private Billing latestBill;
}
