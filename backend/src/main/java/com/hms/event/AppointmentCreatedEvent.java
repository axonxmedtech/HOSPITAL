package com.hms.event;

public class AppointmentCreatedEvent {
    private final Long hospitalId;
    private final Long patientId;
    private final Long appointmentId;

    public AppointmentCreatedEvent(Long hospitalId, Long patientId, Long appointmentId) {
        this.hospitalId = hospitalId;
        this.patientId = patientId;
        this.appointmentId = appointmentId;
    }

    public Long getHospitalId() { return hospitalId; }
    public Long getPatientId() { return patientId; }
    public Long getAppointmentId() { return appointmentId; }
}
