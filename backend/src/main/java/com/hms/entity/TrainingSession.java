package com.hms.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalTime;

/** Scheduled run of a {@link TrainingMaster} course (Form 04 core). */
@Entity
@Table(name = "training_session")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TrainingSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "hospital_id", nullable = false)
    private Long hospitalId;

    @Column(name = "training_master_id", nullable = false)
    private Long trainingMasterId;

    @Column(name = "trainer_id", nullable = false)
    private Long trainerId;

    @Column(name = "session_date", nullable = false)
    private LocalDate sessionDate;

    @Column(name = "start_time", nullable = false)
    private LocalTime startTime;

    @Column(name = "end_time", nullable = false)
    private LocalTime endTime;

    @Column(name = "venue", nullable = false, length = 100)
    private String venue;

    // PLANNED / IN_PROGRESS / COMPLETED / CANCELLED
    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @Column(name = "cancel_reason", columnDefinition = "TEXT")
    private String cancelReason;
}
