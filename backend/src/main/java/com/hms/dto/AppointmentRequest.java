package com.hms.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.hms.entity.Appointment;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * What a client may send to book an appointment, including the details of a patient who is not
 * registered yet. Server-owned fields (id, hospital, status, active flag, creation time) are not
 * accepted. Date and time formats match the entity, so the screens send the same JSON as before.
 */
@Data
public class AppointmentRequest {
    private Long patientId;
    private Long doctorId;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate appointmentDate;

    @JsonFormat(pattern = "HH:mm")
    private LocalTime appointmentTime;

    private String notes;

    // A patient registered while booking.
    private String patientName;
    private String patientPhone;
    private String patientEmail;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate patientDateOfBirth;

    private String patientGender;

    /** A new, unsaved appointment holding only the client-supplied fields. */
    public Appointment toEntity() {
        Appointment a = new Appointment();
        a.setPatientId(patientId);
        a.setDoctorId(doctorId);
        a.setAppointmentDate(appointmentDate);
        a.setAppointmentTime(appointmentTime);
        a.setNotes(notes);
        a.setPatientName(patientName);
        a.setPatientPhone(patientPhone);
        a.setPatientEmail(patientEmail);
        a.setPatientDateOfBirth(patientDateOfBirth);
        a.setPatientGender(patientGender);
        return a;
    }
}
