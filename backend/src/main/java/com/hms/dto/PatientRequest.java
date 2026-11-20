package com.hms.dto;

import com.hms.entity.Patient;
import lombok.Data;

import java.time.LocalDate;

/**
 * What a client may send to register or edit a patient.
 *
 * <p>The endpoints used to bind the JPA entity directly, so a request could also carry
 * server-owned fields (id, hospital, status, active flag, creation time). Only the fields below
 * are accepted now; anything else in the body is ignored. The JSON shape the screens send is
 * unchanged.
 */
@Data
public class PatientRequest {
    private String name;
    private LocalDate dateOfBirth;
    private String gender;
    private String phone;
    private String email;
    private String address;
    private String medicalHistory;

    /** A new, unsaved patient holding only the client-supplied fields. */
    public Patient toEntity() {
        Patient p = new Patient();
        p.setName(name);
        p.setDateOfBirth(dateOfBirth);
        p.setGender(gender);
        p.setPhone(phone);
        p.setEmail(email);
        p.setAddress(address);
        p.setMedicalHistory(medicalHistory);
        return p;
    }
}
