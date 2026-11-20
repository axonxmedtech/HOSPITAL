package com.hms.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/**
 * DTO for create/update patient requests.
 * Accepts only the user-supplied fields — server-side fields (id, hospitalId,
 * publicId, customId, isActive, createdAt, duplicatePhoneAck*) are excluded,
 * eliminating the S4684 mass-assignment risk.
 */
public class PatientRequest {

    @NotBlank(message = "Name is required")
    @Size(max = 200, message = "Name is too long")
    private String name;

    @NotBlank(message = "Phone number is required")
    @Pattern(regexp = "^[0-9+\\- ]{7,15}$", message = "Invalid phone number")
    private String phone;

    private String gender;
    private String email;
    private String address;
    private String medicalHistory;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate dateOfBirth;

    // Getters and setters

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

    public String getGender() { return gender; }
    public void setGender(String gender) { this.gender = gender; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }

    public String getMedicalHistory() { return medicalHistory; }
    public void setMedicalHistory(String medicalHistory) { this.medicalHistory = medicalHistory; }

    public LocalDate getDateOfBirth() { return dateOfBirth; }
    public void setDateOfBirth(LocalDate dateOfBirth) { this.dateOfBirth = dateOfBirth; }

    /**
     * Map this DTO onto a fresh Patient entity so the existing service layer
     * receives the same type without modification.
     */
    public com.hms.entity.Patient toEntity() {
        com.hms.entity.Patient p = new com.hms.entity.Patient();
        p.setName(name);
        p.setPhone(phone);
        p.setGender(gender);
        p.setEmail(email);
        p.setAddress(address);
        p.setMedicalHistory(medicalHistory);
        p.setDateOfBirth(dateOfBirth);
        return p;
    }
}

