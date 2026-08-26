package com.hms.dto;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * One row of the nurse's medication chart: a doctor-authored prescription plus
 * its course progress and today's administration state. Includes STOPPED and
 * COMPLETED orders (never deleted) so the full history is visible.
 */
@Data
public class MedicationChartItemDTO {
    private Long prescriptionId;
    private String medicineName;
    private String dosage;
    private String frequency;
    private String duration;
    private String route;
    private String type;
    private String instructions;

    /** BEFORE_FOOD / AFTER_FOOD / WITH_FOOD / NOT_SPECIFIED, or null on an order predating it. */
    private String foodTiming;

    private String status;          // ACTIVE / STOPPED / COMPLETED
    private LocalDate startDate;
    private Integer durationDays;

    private Integer dayOfCourse;    // 1-based day within the course (null if no start date)
    private boolean courseActive;   // ACTIVE and still within the duration window
    private boolean administeredToday;
    private boolean pending;        // courseActive && not administered today → reminder

    private LocalDateTime lastAdministeredAt;
    private String lastAdministeredStatus;
}
