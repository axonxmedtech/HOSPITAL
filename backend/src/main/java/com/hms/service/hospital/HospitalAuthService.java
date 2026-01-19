package com.hms.service.hospital;

import com.hms.dto.LoginRequest;
import com.hms.dto.LoginResponse;
import com.hms.entity.Hospital;
import com.hms.entity.User;
import com.hms.repository.HospitalRepository;
import com.hms.repository.UserRepository;
import com.hms.security.JwtUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * HospitalAuthService - Authentication service for Hospital users
 * 
 * This service handles login for Hospital users:
 * - Hospital Admin (role = HOSPITAL_ADMIN)
 * - Doctor (role = DOCTOR)
 * 
 * All hospital users must have a valid hospital_id.
 * 
 * @author HMS Team
 * @version Phase-1
 */
@Service
public class HospitalAuthService {

    private static final Logger logger = LoggerFactory.getLogger(HospitalAuthService.class);

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private HospitalRepository hospitalRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtUtil jwtUtil;

    /**
     * Authenticate Hospital user and generate JWT token
     * 
     * @param request LoginRequest containing email and password
     * @return LoginResponse with JWT token and user details
     * @throws RuntimeException if credentials are invalid, user is not a hospital
     *                          user, or hospital is inactive
     */
    public LoginResponse login(LoginRequest request) {
        logger.info("Hospital login attempt for email: {}", request.getEmail());

        // Find user by email
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> {
                    logger.warn("Login failed - user not found: {}", request.getEmail());
                    return new RuntimeException("Invalid credentials");
                });

        // Verify password
        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            logger.warn("Login failed - invalid password for user: {}", request.getEmail());
            throw new RuntimeException("Invalid credentials");
        }

        logger.debug("Password verified for user: {}", request.getEmail());

        // Verify user is a hospital user (not Super Admin)
        if ("SUPER_ADMIN".equals(user.getRole())) {
            logger.warn("Login failed - Super Admin tried to login via hospital endpoint: {}", request.getEmail());
            throw new RuntimeException("Access denied. Please use platform login.");
        }

        // Verify user has a hospital_id
        if (user.getHospitalId() == null) {
            logger.error("Login failed - hospital user has null hospital_id: {}", request.getEmail());
            throw new RuntimeException("Invalid hospital user account");
        }

        logger.debug("User role and hospital_id validated for: {}", request.getEmail());

        // Verify hospital exists and is active
        Hospital hospital = hospitalRepository.findById(user.getHospitalId())
                .orElseThrow(() -> {
                    logger.error("Login failed - hospital not found for ID: {}", user.getHospitalId());
                    return new RuntimeException("Hospital not found");
                });

        if (hospital.getIsActive() == null || !hospital.getIsActive()) {
            logger.warn("Login failed - hospital is inactive: {} (ID: {})", hospital.getName(), hospital.getId());
            throw new RuntimeException("Hospital is inactive. Please contact support.");
        }

        // Verify user account is active (handle null as active for backward
        // compatibility)
        if (user.getIsActive() != null && !user.getIsActive()) {
            logger.warn("Login failed - user account is inactive: {}", request.getEmail());
            throw new RuntimeException("User account is inactive. Please contact administrator.");
        }

        logger.info("Login successful for user: {} at hospital: {}", request.getEmail(), hospital.getName());

        // Generate JWT token with hospital_id and modules
        String token = jwtUtil.generateToken(
                user.getId(),
                user.getEmail(),
                user.getRole(),
                user.getHospitalId(), // Include hospital_id for multi-tenant filtering
                hospital.getModules());

        // Create response
        LoginResponse response = new LoginResponse();
        response.setToken(token);
        response.setUserId(user.getId());
        response.setName(user.getName());
        response.setEmail(user.getEmail());
        response.setRole(user.getRole());
        response.setHospitalId(user.getHospitalId());
        response.setHospitalName(hospital.getName());
        response.setModules(hospital.getModules());

        return response;
    }

    /**
     * Get current user profile with fresh hospital status/modules
     * 
     * @param email Email of the authenticated user
     * @return LoginResponse with updated details (token is null or ignored)
     */
    public LoginResponse getProfile(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));

        if (user.getHospitalId() == null) {
            throw new RuntimeException("Invalid hospital user");
        }

        Hospital hospital = hospitalRepository.findById(user.getHospitalId())
                .orElseThrow(() -> new RuntimeException("Hospital not found"));

        if (hospital.getIsActive() == null || !hospital.getIsActive()) {
            throw new RuntimeException("Hospital is inactive");
        }

        if (user.getIsActive() != null && !user.getIsActive()) {
            throw new RuntimeException("User account is inactive");
        }

        LoginResponse response = new LoginResponse();
        // We don't need a new token, just profile data. Or request new token?
        // For polling, we just want data. Token refresh is separate concern.
        response.setToken(null);
        response.setUserId(user.getId());
        response.setName(user.getName());
        response.setEmail(user.getEmail());
        response.setRole(user.getRole());
        response.setHospitalId(user.getHospitalId());
        response.setHospitalName(hospital.getName());
        response.setModules(hospital.getModules());

        return response;
    }
}
