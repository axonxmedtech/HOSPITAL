package com.hms.dto;

public class WhatsAppStatsDTO {
    private long failedToday;
    private long affectedHospitalsToday;

    public WhatsAppStatsDTO(long failedToday, long affectedHospitalsToday) {
        this.failedToday = failedToday;
        this.affectedHospitalsToday = affectedHospitalsToday;
    }

    public long getFailedToday() { return failedToday; }
    public long getAffectedHospitalsToday() { return affectedHospitalsToday; }
}
