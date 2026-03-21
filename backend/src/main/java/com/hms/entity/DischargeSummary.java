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

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
