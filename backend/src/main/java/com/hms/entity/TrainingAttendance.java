package com.hms.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * One employee's attendance row for a {@link TrainingSession} (Form 04 core). BR-7: once
 * trainer-verified, only an audited HR correction (with reason) may change it.
 */
@Entity
@Table(name = "training_attendance")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TrainingAttendance {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "hospital_id", nullable = false)
    private Long hospitalId;

    @Column(name = "session_id", nullable = false)
    private Long sessionId;

    @Column(name = "employee_id", nullable = false)
    private Long employeeId;

    @Column(name = "department", length = 50)
    private String department;

    // PRESENT / ABSENT / LATE
    @Column(name = "attendance_status", nullable = false, length = 20)
    private String attendanceStatus;

    @Column(name = "check_in_time")
    private LocalDateTime checkInTime;

    @Column(name = "check_out_time")
    private LocalDateTime checkOutTime;

    @Column(name = "verified", nullable = false)
    private boolean verified;

    @Column(name = "remarks", columnDefinition = "TEXT")
    private String remarks;
}
