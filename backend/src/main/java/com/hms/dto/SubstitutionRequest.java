package com.hms.dto;

import java.time.LocalDate;

public class SubstitutionRequest {
    private Long primaryNurseProfileId;
    private Long replacementNurseProfileId;
    private LocalDate fromDate;
    private LocalDate toDate;
    private String reason;

    public Long getPrimaryNurseProfileId() { return primaryNurseProfileId; }
    public void setPrimaryNurseProfileId(Long primaryNurseProfileId) { this.primaryNurseProfileId = primaryNurseProfileId; }
    public Long getReplacementNurseProfileId() { return replacementNurseProfileId; }
    public void setReplacementNurseProfileId(Long replacementNurseProfileId) { this.replacementNurseProfileId = replacementNurseProfileId; }
    public LocalDate getFromDate() { return fromDate; }
    public void setFromDate(LocalDate fromDate) { this.fromDate = fromDate; }
    public LocalDate getToDate() { return toDate; }
    public void setToDate(LocalDate toDate) { this.toDate = toDate; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
}
