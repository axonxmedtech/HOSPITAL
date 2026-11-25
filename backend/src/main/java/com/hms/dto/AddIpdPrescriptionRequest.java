package com.hms.dto;

import java.time.LocalDate;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public class AddIpdPrescriptionRequest {
    private Long medicineId;

    /** Hospital-inventory item (saline, IV fluid, consumable) the doses are given from, by name. */
    @jakarta.validation.constraints.Size(max = 150)
    private String hospitalItemName;

    /** Units per dose taken from stock (1 tablet, 1 bottle). Defaults to 1. */
    @jakarta.validation.constraints.Positive
    private Integer quantityPerDose;
    private String medicineName; // explicit name (used when doctor types manually without selecting from inventory)

    @NotBlank(message = "Type is required")
    @Pattern(regexp = "^(?i)(TABLET|CAPSULE|SYRUP|INJECTION|OINTMENT|DROPS|INHALER|IV_FLUID)$", message = "Invalid medicine type")
    private String type; // TABLET / INJECTION / IV_FLUID

    @NotBlank(message = "Route is required")
    @Pattern(regexp = "^(?i)(ORAL|IV|IM|TOPICAL|SUBCUTANEOUS|INHALATION|OPHTHALMIC|OTIC)$", message = "Invalid administration route")
    private String route; // ORAL / IV / IM

    @NotBlank(message = "Dose is required")
    private String dose;

    @NotBlank(message = "Frequency is required")
    private String frequency;

    @NotNull(message = "Duration in days is required")
    @Min(value = 1, message = "Duration must be at least 1 day")
    private Integer durationDays;

    @NotNull(message = "Start date is required")
    private LocalDate startDate;

    private String foodTiming;

    public String getFoodTiming() { return foodTiming; }
    public void setFoodTiming(String foodTiming) { this.foodTiming = foodTiming; }

    public String getHospitalItemName() { return hospitalItemName; }
    public void setHospitalItemName(String hospitalItemName) { this.hospitalItemName = hospitalItemName; }
    public Integer getQuantityPerDose() { return quantityPerDose; }
    public void setQuantityPerDose(Integer quantityPerDose) { this.quantityPerDose = quantityPerDose; }
    public Long getMedicineId() { return medicineId; }
    public void setMedicineId(Long medicineId) { this.medicineId = medicineId; }

    public String getMedicineName() { return medicineName; }
    public void setMedicineName(String medicineName) { this.medicineName = medicineName; }

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

    /** Optional: the same value on every retry of one act of prescribing (RequestIdempotencyService). */
    @jakarta.validation.constraints.Size(max = 64)
    private String idempotencyKey;

    public String getIdempotencyKey() { return idempotencyKey; }
    public void setIdempotencyKey(String idempotencyKey) { this.idempotencyKey = idempotencyKey; }
}
