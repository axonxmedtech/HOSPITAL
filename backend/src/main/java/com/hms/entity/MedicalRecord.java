package com.hms.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * MedicalRecord - Entity to store clinical details of a patient visit (OPD)
 * 
 * Captures symptoms, diagnosis, and treatment notes.
 * Linked to an Appointment (usually) and a Patient.
 * 
 * @author HMS Team
 * @version Phase-3
 */
@Entity
@Table(name = "medical_records")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MedicalRecord {

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

    @Column(name = "patient_id", nullable = false)
    private Long patientId;

    @Column(name = "doctor_id", nullable = false)
    private Long doctorId;

    @Column(name = "appointment_id", unique = true)
    private Long appointmentId;

    @Column(name = "opd_id", unique = true)
    private Long opdId;

    @Column(length = 1000)
    private String symptoms;

    @Column(length = 1000)
    private String diagnosis;

    @Column(length = 2000)
    private String treatmentNotes;

    private LocalDate followUpDate;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
