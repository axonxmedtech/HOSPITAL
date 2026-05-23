package com.hms.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * LoginResponse - DTO for login responses
 * 
 * This DTO contains the JWT token and user information after successful login.
 * Used for both Super Admin and Hospital user login responses.
 * 
 * @author HMS Team
 * @version Phase-1
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LoginResponse {

    /**
     * JWT authentication token
     */
    private String token;

    /**
     * User ID
     */
    private Long userId;

    /**
     * User's name
     */
    private String name;

    /**
     * User's email
     */
    private String email;

    /**
     * User's role (SUPER_ADMIN, HOSPITAL_ADMIN, DOCTOR)
     */
    private String role;

    /**
     * Hospital ID (null for Super Admin)
     */
    private Long hospitalId;

    /**
     * Hospital name (null for Super Admin)
     */
    /**
     * Hospital name (null for Super Admin)
     */
    private String hospitalName;

    /**
     * Enabled modules for the hospital (e.g., ["OPD", "BILLING", "IPD"])
     */
    private java.util.List<String> modules;

    /**
     * Operational mode for hospital (receptionMode enums HAS_RECEPTIONIST or SOLO)
     */
    private String receptionMode;

    /**
     * Billing responsibility (billingHandler enums RECEPTIONIST or DOCTOR)
     */
    private String billingHandler;
}
