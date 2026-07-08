package com.hms.dto;

import java.time.LocalDate;

public class TempWardAssignmentRequest {
    private Long nurseProfileId;
    private Long tempWardId;
    private LocalDate fromDate;
    private LocalDate toDate;
    private String reason;

    public Long getNurseProfileId() { return nurseProfileId; }
    public void setNurseProfileId(Long nurseProfileId) { this.nurseProfileId = nurseProfileId; }
    public Long getTempWardId() { return tempWardId; }
    public void setTempWardId(Long tempWardId) { this.tempWardId = tempWardId; }
    public LocalDate getFromDate() { return fromDate; }
    public void setFromDate(LocalDate fromDate) { this.fromDate = fromDate; }
    public LocalDate getToDate() { return toDate; }
    public void setToDate(LocalDate toDate) { this.toDate = toDate; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
}
