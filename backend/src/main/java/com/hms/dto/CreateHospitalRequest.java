package com.hms.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * CreateHospitalRequest - DTO for creating a new hospital
 * 
 * This DTO is used by Super Admin to create a new hospital.
 * It includes hospital details and the initial hospital admin credentials.
 * 
 * @author HMS Team
 * @version Phase-1
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateHospitalRequest {

    /**
     * Name of the hospital
     */
    @NotBlank(message = "Hospital name is required")
    private String hospitalName;

    /**
     * Email for the hospital admin user
     */
    @NotBlank(message = "Admin email is required")
    @Email(message = "Invalid email format")
    private String adminEmail;

    /**
     * Password for the hospital admin user
     */
    @NotBlank(message = "Admin password is required")
    private String adminPassword;

    /**
     * Name of the hospital admin user
     */
    /**
     * Name of the hospital admin user
     */
    @NotBlank(message = "Admin name is required")
    private String adminName;

    /**
     * Optional: List of modules to enable.
     * If null/empty, defaults to CMS mode (OPD, BILLING).
     */
    private java.util.List<String> modules;

    /**
     * Whether the hospital is a single doctor hospital.
     */
    private Boolean isSingleDoctor = false;
}
