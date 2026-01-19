package com.hms.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * AddDoctorRequest - DTO for adding a new doctor
 * 
 * This DTO includes doctor information and password for creating a user
 * account.
 * Used by Hospital Admin to add doctors to their hospital.
 * 
 * @author HMS Team
 * @version Phase-1
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AddDoctorRequest {

    /**
     * Doctor's full name
     */
    @NotBlank(message = "Name is required")
    private String name;

    /**
     * Doctor's specialization
     */
    @NotBlank(message = "Specialization is required")
    private String specialization;

    /**
     * Doctor's phone number
     */
    @NotBlank(message = "Phone is required")
    private String phone;

    /**
     * Doctor's email address (used for login)
     */
    @NotBlank(message = "Email is required")
    @Email(message = "Invalid email format")
    private String email;

    /**
     * Password for doctor's user account
     */
    @NotBlank(message = "Password is required")
    private String password;
}
