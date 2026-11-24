package com.hms.dto;

import java.time.LocalDateTime;
import lombok.Data;

/**
 * One job on a nurse's "due now" list: a dose, a vitals round or a sample to collect, for one
 * patient. {@code state} says where it stands against the clock (see NurseWorklistService).
 */
@Data
public class NurseWorklistItemDTO {
    public static final String DOSE = "DOSE";
    public static final String VITALS = "VITALS";
    public static final String SAMPLE = "SAMPLE";

    private String kind;
    /** OVERDUE, DUE, UPCOMING, DONE, AS_NEEDED or UNSCHEDULED. */
    private String state;

    private Long ipdAdmissionId;
    private String patientName;
    private String ipdNumber;
    private Long wardId;
    private String wardName;
    private String bedCode;

    // DOSE
    private Long prescriptionId;
    private String medicineName;
    private String dosage;
    private String route;
    private String type;
    private String foodTiming;
    /** MORNING / AFTERNOON / NIGHT, or null for an as-needed or unscheduled order. */
    private String slot;
    private LocalDateTime scheduledTime;
    private Integer quantityPerDose;
    /** Linked to hospital stock (a given dose comes off stock and onto the bill). */
    private boolean stocked;
    /** What was recorded for this dose: GIVEN, REFUSED, SKIPPED, ... (DONE items only). */
    private String recordedStatus;
    private LocalDateTime recordedAt;
    /** As-needed orders: how many doses were given today. */
    private Integer givenToday;

    // VITALS
    private LocalDateTime lastRecordedAt;

    // SAMPLE
    private String investigationPublicId;
    private String testName;
    private String priority;
    private LocalDateTime orderedAt;
}
