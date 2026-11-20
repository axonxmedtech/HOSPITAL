package com.hms.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hms.entity.Appointment;
import com.hms.entity.HospitalInventory;
import com.hms.entity.HospitalInventoryPurchase;
import com.hms.entity.Patient;
import com.hms.entity.PatientStatus;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

import java.time.LocalDate;
import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The create/update endpoints for patients, appointments and hospital inventory take these request
 * classes instead of the JPA entities (SonarCloud java:S4684). A body that also carries
 * server-owned fields — an id, another hospital, a status — must lose them, while the JSON the
 * screens actually send keeps binding exactly as before.
 */
class RequestDtoBindingTest {

    /** Built the way Spring Boot builds the app's mapper (unknown fields ignored, java.time). */
    private final ObjectMapper mapper = Jackson2ObjectMapperBuilder.json().build();

    @Test
    void aPatientRequestCannotSetServerOwnedFields() throws Exception {
        Patient p = mapper.readValue("""
                {"id": 99, "publicId": "x", "customId": "PAT-FAKE", "hospitalId": 7,
                 "status": "DISCHARGED", "isActive": false, "createdAt": "2000-01-01T00:00:00",
                 "name": "Asha", "dateOfBirth": "1990-05-10", "gender": "Female",
                 "phone": "9822012345", "email": "a@b.in", "address": "Pune", "medicalHistory": "none"}
                """, PatientRequest.class).toEntity();
        assertThat(p.getId()).isNull();
        assertThat(p.getPublicId()).isNull();
        assertThat(p.getCustomId()).isNull();
        assertThat(p.getHospitalId()).isNull();
        assertThat(p.getStatus()).isEqualTo(PatientStatus.REGISTERED);
        assertThat(p.getIsActive()).isTrue();
        assertThat(p.getName()).isEqualTo("Asha");
        assertThat(p.getDateOfBirth()).isEqualTo(LocalDate.of(1990, 5, 10));
        assertThat(p.getPhone()).isEqualTo("9822012345");
        assertThat(p.getMedicalHistory()).isEqualTo("none");
    }

    @Test
    void anAppointmentRequestKeepsItsFormatsAndDropsServerOwnedFields() throws Exception {
        Appointment a = mapper.readValue("""
                {"id": 5, "hospitalId": 7, "status": "COMPLETED", "isActive": false,
                 "patientId": 12, "doctorId": 3, "appointmentDate": "2026-11-21",
                 "appointmentTime": "10:30", "notes": "follow up",
                 "patientName": "New Person", "patientPhone": "9822000000",
                 "patientDateOfBirth": "1985-01-02", "patientGender": "Male"}
                """, AppointmentRequest.class).toEntity();
        assertThat(a.getId()).isNull();
        assertThat(a.getHospitalId()).isNull();
        assertThat(a.getStatus()).isEqualTo("SCHEDULED");
        assertThat(a.getIsActive()).isTrue();
        assertThat(a.getAppointmentDate()).isEqualTo(LocalDate.of(2026, 11, 21));
        assertThat(a.getAppointmentTime()).isEqualTo(LocalTime.of(10, 30));
        assertThat(a.getPatientDateOfBirth()).isEqualTo(LocalDate.of(1985, 1, 2));
        assertThat(a.getPatientId()).isEqualTo(12L);
        assertThat(a.getDoctorId()).isEqualTo(3L);
        assertThat(a.getNotes()).isEqualTo("follow up");
    }

    @Test
    void anInventoryRequestCannotTargetAnotherRow() throws Exception {
        HospitalInventory i = mapper.readValue("""
                {"id": 41, "hospitalId": 9, "name": "Saline 500ml", "stockQuantity": 20,
                 "unitPrice": 45.5, "expiryDate": "2027-01-31", "minStockLevel": 5,
                 "type": "Consumable", "manufacturer": "Baxter"}
                """, HospitalInventoryRequest.class).toEntity();
        assertThat(i.getId()).isNull();
        assertThat(i.getHospitalId()).isNull();
        assertThat(i.getIsActive()).isTrue(); // absent -> active, as before
        assertThat(i.getStockQuantity()).isEqualTo(20);
        assertThat(i.getExpiryDate()).isEqualTo(LocalDate.of(2027, 1, 31));

        HospitalInventory off = mapper.readValue("{\"name\":\"x\",\"isActive\":false}",
                HospitalInventoryRequest.class).toEntity();
        assertThat(off.getIsActive()).isFalse();

        HospitalInventoryPurchase p = mapper.readValue("""
                {"id": 3, "hospitalId": 9, "name": "Gloves", "quantity": 100, "unitPrice": 2.0}
                """, HospitalInventoryPurchaseRequest.class).toEntity();
        assertThat(p.getId()).isNull();
        assertThat(p.getHospitalId()).isNull();
        assertThat(p.getQuantity()).isEqualTo(100);
    }
}
