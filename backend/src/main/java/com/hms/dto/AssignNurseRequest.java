package com.hms.dto;

/**
 * Request to assign (or reassign) a nurse to an IPD admission.
 * ipdAdmissionId is required on assign; on reassign the admission is derived
 * from the existing assignment, so only nurseUserId + notes are used.
 */
public class AssignNurseRequest {
    private Long ipdAdmissionId;
    private Long nurseUserId;
    private String notes;

    public Long getIpdAdmissionId() { return ipdAdmissionId; }
    public void setIpdAdmissionId(Long ipdAdmissionId) { this.ipdAdmissionId = ipdAdmissionId; }

    public Long getNurseUserId() { return nurseUserId; }
    public void setNurseUserId(Long nurseUserId) { this.nurseUserId = nurseUserId; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
}
