package com.hms.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "opd")
public class Opd {

    public enum VisitType { NEW, FOLLOWUP }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "case_id", unique = true)
    private String caseId;

    @ManyToOne
    @JoinColumn(name = "patient_id")
    private Patient patient;

    @ManyToOne
    @JoinColumn(name = "receptionist_id")
    private User receptionist;

    @ManyToOne
    @JoinColumn(name = "doctor_id")
    private Doctor doctor;

    // Vitals captured per OPD (can change every visit)
    @Column(name = "bp")
    private String bp;

    @Column(name = "temperature")
    private Double temperature;

    @Column(name = "pulse")
    private Integer pulse;

    @Column(name = "weight")
    private Double weight;

    @Column(name = "spo2")
    private Integer spo2;

    @Column(name = "problem", columnDefinition = "text")
    private String problem;

    @Enumerated(EnumType.STRING)
    @Column(name = "visit_type")
    private VisitType visitType;

    @Column(name = "token_number")
    private Integer tokenNumber;

    public enum Status { QUEUED, CONSULTED, COMPLETED }

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private Status status = Status.QUEUED;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    public Opd() {
        this.createdAt = LocalDateTime.now();
    }

    // Getters and setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getCaseId() { return caseId; }
    public void setCaseId(String caseId) { this.caseId = caseId; }
    public Patient getPatient() { return patient; }
    public void setPatient(Patient patient) { this.patient = patient; }
    public User getReceptionist() { return receptionist; }
    public void setReceptionist(User receptionist) { this.receptionist = receptionist; }
    public Doctor getDoctor() { return doctor; }
    public void setDoctor(Doctor doctor) { this.doctor = doctor; }
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
    public VisitType getVisitType() { return visitType; }
    public void setVisitType(VisitType visitType) { this.visitType = visitType; }
    public Integer getTokenNumber() { return tokenNumber; }
    public void setTokenNumber(Integer tokenNumber) { this.tokenNumber = tokenNumber; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }
}
