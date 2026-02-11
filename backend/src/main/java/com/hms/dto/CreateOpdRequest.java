package com.hms.dto;

public class CreateOpdRequest {
    private Long patientId;
    private Long receptionistId;
    private Long doctorId;
    private String bp;
    private Double temperature;
    private Integer pulse;
    private Double weight;
    private Integer spo2;
    private String problem;
    private String visitType; // NEW or FOLLOWUP

    public Long getPatientId() { return patientId; }
    public void setPatientId(Long patientId) { this.patientId = patientId; }
    public Long getReceptionistId() { return receptionistId; }
    public void setReceptionistId(Long receptionistId) { this.receptionistId = receptionistId; }
    public Long getDoctorId() { return doctorId; }
    public void setDoctorId(Long doctorId) { this.doctorId = doctorId; }
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
