package com.hms.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "discharge_summary")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DischargeSummary {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "ipd_admission_id", nullable = false, unique = true)
    private Long ipdAdmissionId;

    @Column(name = "final_diagnosis", columnDefinition = "text")
    private String finalDiagnosis;

    @Column(name = "treatment_given", columnDefinition = "text")
    private String treatmentGiven;

    @Column(name = "discharge_notes", columnDefinition = "text")
    private String dischargeNotes;

    @Column(name = "follow_up_date")
    private LocalDate followUpDate;

    /** IMPROVED / STABLE / UNCHANGED / DETERIORATED — not asked for a death or an absconding. */
    @Column(name = "condition_at_discharge", length = 30)
    private String conditionAtDischarge;

    @Column(name = "procedures_done", columnDefinition = "text")
    private String proceduresDone;

    /** Key investigation results the next doctor needs. */
    @Column(name = "investigations", columnDefinition = "text")
    private String investigations;

    /** Diet, activity, wound care and when to come back early. */
    @Column(name = "advice", columnDefinition = "text")
    private String advice;

    /** Medicines to take at home: JSON list of {name, dose, frequency, durationDays, instructions}. */
    @Column(name = "discharge_medications", columnDefinition = "text")
    private String dischargeMedications;

    @Column(name = "follow_up_time")
    private java.time.LocalTime followUpTime;

    /** The appointment booked for the follow-up when the discharge was confirmed. */
    @Column(name = "follow_up_appointment_id")
    private Long followUpAppointmentId;

    @Column(name = "prepared_by_doctor_id")
    private Long preparedByDoctorId;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
