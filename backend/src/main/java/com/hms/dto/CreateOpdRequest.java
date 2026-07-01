package com.hms.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public class CreateOpdRequest {
    @NotBlank(message = "Patient ID is required")
    private String patientId;
    private Long receptionistId;
    private String doctorId;

    @Pattern(regexp = "^(\\d{2,3})\\s*/\\s*(\\d{2,3})$", message = "Blood pressure must be in format Systolic/Diastolic, e.g., 120/80")
    private String bp;

    @DecimalMin(value = "30.0", message = "Temperature must be at least 30.0°C")
    @DecimalMax(value = "45.0", message = "Temperature cannot exceed 45.0°C")
    private Double temperature;

    @Min(value = 30, message = "Pulse must be at least 30 bpm")
    @Max(value = 250, message = "Pulse cannot exceed 250 bpm")
    private Integer pulse;

    @DecimalMin(value = "0.1", message = "Weight must be at least 0.1 kg")
    @DecimalMax(value = "500.0", message = "Weight cannot exceed 500.0 kg")
    private Double weight;

    @Min(value = 0, message = "SpO2 cannot be negative")
    @Max(value = 100, message = "SpO2 cannot exceed 100%")
    private Integer spo2;

    private String problem;

    @NotBlank(message = "Visit type is required")
    private String visitType; // NEW or FOLLOWUP

    public String getPatientId() { return patientId; }
    public void setPatientId(String patientId) { this.patientId = patientId; }
    public Long getReceptionistId() { return receptionistId; }
    public void setReceptionistId(Long receptionistId) { this.receptionistId = receptionistId; }
    public String getDoctorId() { return doctorId; }
    public void setDoctorId(String doctorId) { this.doctorId = doctorId; }
    public String getBp() { return bp; }
    public void setBp(String bp) { this.bp = bp; }
    public Double getTemperature() { return temperature; }
    public void setTemperature(Double temperature) { this.temperature = temperature; }
    public Integer getPulse() { return pulse; }
    public void setPulse(Integer pulse) { this.pulse = pulse; }
    public Double getWeight() { return weight; }
    public void setWeight(Double weight) { this.weight = weight; }
    public Integer getSpo2() { return spo2; }
    public void setSpo2(Integer spo2) { this.spo2 = spo2; }
    public String getProblem() { return problem; }
    public void setProblem(String problem) { this.problem = problem; }
    public String getVisitType() { return visitType; }
    public void setVisitType(String visitType) { this.visitType = visitType; }
}
