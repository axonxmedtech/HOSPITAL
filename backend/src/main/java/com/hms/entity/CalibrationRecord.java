package com.hms.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** Legal compliance calibration certificate (Form 36 core). BR-2: drives the overdue lock. */
@Entity
@Table(name = "calibration_record")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CalibrationRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "hospital_id", nullable = false)
    private Long hospitalId;

    @Column(name = "equipment_id", nullable = false)
    private Long equipmentId;

    @Column(name = "calibration_date", nullable = false)
    private LocalDate calibrationDate;

    @Column(name = "due_date", nullable = false)
    private LocalDate dueDate;

    @Column(name = "agency", nullable = false, length = 100)
    private String agency;

    @Column(name = "certificate_reference", length = 100)
    private String certificateReference;

    // PASS / FAIL
    @Column(name = "result", nullable = false, length = 10)
    private String result;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
