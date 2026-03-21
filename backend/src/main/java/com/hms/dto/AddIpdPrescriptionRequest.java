package com.hms.dto;

import java.time.LocalDate;

public class AddIpdPrescriptionRequest {
    private Long medicineId;
    private String type; // TABLET / INJECTION / IV_FLUID
    private String route; // ORAL / IV / IM
    private String dose;
    private String frequency;
    private Integer durationDays;
    private LocalDate startDate;

    public Long getMedicineId() { return medicineId; }
    public void setMedicineId(Long medicineId) { this.medicineId = medicineId; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public String getRoute() { return route; }
    public void setRoute(String route) { this.route = route; }

    public String getDose() { return dose; }
    public void setDose(String dose) { this.dose = dose; }

    public String getFrequency() { return frequency; }
    public void setFrequency(String frequency) { this.frequency = frequency; }

    public Integer getDurationDays() { return durationDays; }
    public void setDurationDays(Integer durationDays) { this.durationDays = durationDays; }

    public LocalDate getStartDate() { return startDate; }
    public void setStartDate(LocalDate startDate) { this.startDate = startDate; }
}
