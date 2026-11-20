package com.hms.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.LocalDate;
import java.time.LocalTime;

/**
 * DTO for creating a new appointment.
 * Accepts only the fields that come from the browser — server-side fields
 * (id, hospitalId, publicId, customId, isActive, createdAt) are never read
 * from the request body, eliminating the S4684 mass-assignment risk.
 */
public class AppointmentRequest {

    private Long patientId;
    private Long doctorId;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate appointmentDate;

    @JsonFormat(pattern = "HH:mm")
    private LocalTime appointmentTime;

    private String status;
    private String notes;

    // Inline patient creation fields (used when patientId is null)
    private String patientName;
    private String patientPhone;
    private String patientEmail;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate patientDateOfBirth;

    private String patientGender;

    // Getters and setters

    public Long getPatientId() { return patientId; }
    public void setPatientId(Long patientId) { this.patientId = patientId; }

    public Long getDoctorId() { return doctorId; }
    public void setDoctorId(Long doctorId) { this.doctorId = doctorId; }

    public LocalDate getAppointmentDate() { return appointmentDate; }
    public void setAppointmentDate(LocalDate appointmentDate) { this.appointmentDate = appointmentDate; }

    public LocalTime getAppointmentTime() { return appointmentTime; }
    public void setAppointmentTime(LocalTime appointmentTime) { this.appointmentTime = appointmentTime; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    public String getPatientName() { return patientName; }
    public void setPatientName(String patientName) { this.patientName = patientName; }

    public String getPatientPhone() { return patientPhone; }
    public void setPatientPhone(String patientPhone) { this.patientPhone = patientPhone; }

    public String getPatientEmail() { return patientEmail; }
    public void setPatientEmail(String patientEmail) { this.patientEmail = patientEmail; }

    public LocalDate getPatientDateOfBirth() { return patientDateOfBirth; }
    public void setPatientDateOfBirth(LocalDate patientDateOfBirth) { this.patientDateOfBirth = patientDateOfBirth; }

    public String getPatientGender() { return patientGender; }
    public void setPatientGender(String patientGender) { this.patientGender = patientGender; }

    /**
     * Map this DTO onto a fresh Appointment entity so the service receives the
     * same type it always has without touching the service layer.
     */
    public com.hms.entity.Appointment toEntity() {
        com.hms.entity.Appointment a = new com.hms.entity.Appointment();
        a.setPatientId(patientId);
        a.setDoctorId(doctorId);
        a.setAppointmentDate(appointmentDate);
        a.setAppointmentTime(appointmentTime);
        a.setStatus(status);
        a.setNotes(notes);
        a.setPatientName(patientName);
        a.setPatientPhone(patientPhone);
        a.setPatientEmail(patientEmail);
        a.setPatientDateOfBirth(patientDateOfBirth);
        a.setPatientGender(patientGender);
        return a;
    }
}

