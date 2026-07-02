package com.hms.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/** Workforce shift schedule slot (Form 39 core). BR-2: no overlapping/consecutive-night gate. */
@Entity
@Table(name = "shift_roster")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ShiftRoster {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "hospital_id", nullable = false)
    private Long hospitalId;

    @Column(name = "employee_id", nullable = false)
    private Long employeeId;

    @Column(name = "department", nullable = false, length = 50)
    private String department;

    // MORNING / EVENING / NIGHT / ON_CALL
    @Column(name = "shift", nullable = false, length = 20)
    private String shift;

    @Column(name = "date", nullable = false)
    private LocalDate date;

    // SCHEDULED / ON_LEAVE
    @Column(name = "status", nullable = false, length = 20)
    private String status;
}
