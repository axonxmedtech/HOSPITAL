package com.hms.dto;

import lombok.Data;

@Data
public class EmergencyVisitRequest {
    // Registration: either an existing patient...
    private Long patientId;
    // ...or an unknown arrival (BR-2/BR-3): a temporary patient is created immediately
    private Boolean unknownPatient;
    private String unknownLabel;      // e.g. "Unknown Male ~40"
    private String gender;
    private Integer approximateAge;

    private String arrivalMode;
    private Boolean isMlc;
    private String mlcNumber;

    // Triage
    private String triageLevel;
    private String triageCriteria;

    // Assessment
    private String chiefComplaint;
    private String airwayStatus;
    private String breathingStatus;
    private String circulationStatus;
    private Integer gcsScore;
    private String initialDiagnosis;

    // Disposition
    private String disposition;       // ADMIT, ICU, OT, DISCHARGE, REFER, DEATH
    private Long doctorId;            // for admission
    private Long wardId;
    private Long bedId;
}
