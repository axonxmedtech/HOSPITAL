package com.hms.service.hospital;

import com.hms.dto.LoginRequest;
import com.hms.dto.LoginResponse;
import com.hms.dto.ProfileUpdateRequest;
import com.hms.exception.UnauthorizedException;
import com.hms.entity.Hospital;
import com.hms.entity.HospitalSetting;
import com.hms.entity.User;
import com.hms.entity.HospitalAdmin;
import com.hms.entity.Receptionist;
import com.hms.entity.Pharmacist;
import com.hms.entity.Doctor;
import com.hms.repository.HospitalRepository;
import com.hms.repository.HospitalSettingRepository;
import com.hms.repository.UserRepository;
import com.hms.repository.HospitalAdminRepository;
import com.hms.repository.ReceptionistProfileRepository;
import com.hms.repository.PharmacistProfileRepository;
import com.hms.repository.DoctorRepository;
import com.hms.dto.HospitalSettingDTO;
import com.hms.security.JwtUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
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
    private HospitalSettingRepository hospitalSettingRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private HospitalAdminRepository hospitalAdminRepository;

    @Autowired
    private ReceptionistProfileRepository receptionistProfileRepository;

    @Autowired
    private PharmacistProfileRepository pharmacistProfileRepository;

    @Autowired
    private DoctorRepository doctorRepository;

    /**
     * Helper to populate detailed profile fields on LoginResponse
     */
    private void populateProfileDetails(User user, LoginResponse response) {
        if ("SUPER_ADMIN".equals(user.getRole())) {
            return;
        }
        if ("HOSPITAL_ADMIN".equals(user.getRole())) {
            HospitalAdmin admin = hospitalAdminRepository.findByEmail(user.getEmail())
                    .orElseGet(() -> {
                        HospitalAdmin newAdmin = new HospitalAdmin();
                        newAdmin.setHospitalId(user.getHospitalId());
                        newAdmin.setName(user.getName());
                        newAdmin.setEmail(user.getEmail());
                        newAdmin.setPhone("");
                        newAdmin.setAge(null);
                        newAdmin.setGender(null);
                        newAdmin.setIsActive(true);
                        return hospitalAdminRepository.save(newAdmin);
                    });
            response.setPhone(admin.getPhone());
            response.setAge(admin.getAge());
            response.setGender(admin.getGender());
            if (Boolean.TRUE.equals(response.getIsSingleDoctor())) {
                doctorRepository.findByEmailAndHospitalId(user.getEmail(), user.getHospitalId())
                        .ifPresent(doc -> response.setSpecialization(doc.getSpecialization()));
            } else {
                response.setSpecialization(null);
            }
        } else if ("RECEPTIONIST".equals(user.getRole())) {
            Receptionist receptionist = receptionistProfileRepository.findByEmail(user.getEmail())
                    .orElseGet(() -> {
                        Receptionist newRec = new Receptionist();
                        newRec.setHospitalId(user.getHospitalId());
                        newRec.setName(user.getName());
                        newRec.setEmail(user.getEmail());
                        newRec.setPhone("");
                        newRec.setAge(null);
                        newRec.setGender(null);
                        newRec.setIsActive(true);
                        return receptionistProfileRepository.save(newRec);
                    });
            response.setPhone(receptionist.getPhone());
            response.setAge(receptionist.getAge());
            response.setGender(receptionist.getGender());
            response.setSpecialization(null);
        } else if ("PHARMACIST".equals(user.getRole())) {
            Pharmacist pharmacist = pharmacistProfileRepository.findByEmail(user.getEmail())
                    .orElseGet(() -> {
                        Pharmacist newPhar = new Pharmacist();
                        newPhar.setHospitalId(user.getHospitalId());
                        newPhar.setName(user.getName());
                        newPhar.setEmail(user.getEmail());
                        newPhar.setPhone("");
                        newPhar.setAge(null);
                        newPhar.setGender(null);
                        newPhar.setIsActive(true);
                        return pharmacistProfileRepository.save(newPhar);
                    });
            response.setPhone(pharmacist.getPhone());
            response.setAge(pharmacist.getAge());
            response.setGender(pharmacist.getGender());
            response.setSpecialization(null);
        } else if ("DOCTOR".equals(user.getRole())) {
            Doctor doctor = doctorRepository.findByEmailAndHospitalId(user.getEmail(), user.getHospitalId())
                    .orElseThrow(() -> new RuntimeException("Doctor profile not found"));
            response.setPhone(doctor.getPhone());
            response.setSpecialization(doctor.getSpecialization());
            response.setAge(null);
            response.setGender(null);
        }
    }

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

        // Find user by email — same message as wrong password to prevent user enumeration
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> {
                    logger.warn("Login failed - user not found: {}", request.getEmail());
                    return new UnauthorizedException("Invalid credentials");
                });

        // Verify password
        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            logger.warn("Login failed - invalid password for user: {}", request.getEmail());
            throw new UnauthorizedException("Invalid credentials");
        }

        logger.debug("Password verified for user: {}", request.getEmail());

        // Verify user is a hospital user (not Super Admin)
        if ("SUPER_ADMIN".equals(user.getRole())) {
            logger.warn("Login failed - Super Admin tried to login via hospital endpoint: {}", request.getEmail());
            throw new UnauthorizedException("Access denied. Please use platform login.");
        }

        // Verify user has a hospital_id
        if (user.getHospitalId() == null) {
            logger.error("Login failed - hospital user has null hospital_id: {}", request.getEmail());
            throw new UnauthorizedException("Invalid hospital user account");
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
            throw new UnauthorizedException("Hospital is inactive. Please contact support.");
        }

        // Fetch hospital settings (or auto-create default row if not exist)
        HospitalSetting settings = hospitalSettingRepository.findByHospital_Id(hospital.getId())
                .orElseGet(() -> {
                    HospitalSetting newSettings = new HospitalSetting();
                    newSettings.setHospital(hospital);
                    newSettings.setReceptionMode("HAS_RECEPTIONIST");
                    newSettings.setBillingHandler("RECEPTIONIST");
                    newSettings.setInClinic(Boolean.TRUE);
                    return hospitalSettingRepository.save(newSettings);
                });

        // Restrict receptionist login if Solo Doctor mode is active
        if ("RECEPTIONIST".equals(user.getRole()) && "SOLO".equals(settings.getReceptionMode())) {
            logger.warn("Login failed - Receptionist login restricted under Solo Doctor mode: {}", request.getEmail());
            throw new UnauthorizedException("Solo Doctor Mode is active. Receptionist login is restricted.");
        }

        // Verify user account is active (handle null as active for backward compatibility)
        if (user.getIsActive() != null && !user.getIsActive()) {
            logger.warn("Login failed - user account is inactive: {}", request.getEmail());
            throw new UnauthorizedException("User account is inactive. Please contact administrator.");
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
        response.setReceptionMode(settings.getReceptionMode());
        response.setBillingHandler(settings.getBillingHandler());
        response.setIsSingleDoctor(hospital.getIsSingleDoctor());
        response.setInClinic(settings.getInClinic());

        // Populate profile details
        populateProfileDetails(user, response);

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

        LoginResponse response = new LoginResponse();
        response.setToken(null);
        response.setUserId(user.getId());
        response.setName(user.getName());
        response.setEmail(user.getEmail());
        response.setRole(user.getRole());

        // Handle platform Super Admin bypass
        if ("SUPER_ADMIN".equals(user.getRole())) {
            response.setHospitalId(null);
            response.setHospitalName(null);
            response.setModules(null);
            response.setReceptionMode(null);
            response.setBillingHandler(null);
            response.setPhone(null);
            response.setAge(null);
            response.setGender(null);
            response.setSpecialization(null);
            return response;
        }

        if (user.getHospitalId() == null) {
            throw new RuntimeException("Invalid hospital user");
        }

        Hospital hospital = hospitalRepository.findById(user.getHospitalId())
                .orElseThrow(() -> new RuntimeException("Hospital not found"));

        if (hospital.getIsActive() == null || !hospital.getIsActive()) {
            throw new RuntimeException("Hospital is inactive");
        }

        HospitalSetting settings = hospitalSettingRepository.findByHospital_Id(hospital.getId())
                .orElseGet(() -> {
                    HospitalSetting newSettings = new HospitalSetting();
                    newSettings.setHospital(hospital);
                    newSettings.setReceptionMode("HAS_RECEPTIONIST");
                    newSettings.setBillingHandler("RECEPTIONIST");
                    newSettings.setInClinic(Boolean.TRUE);
                    return hospitalSettingRepository.save(newSettings);
                });

        // Restrict receptionist profile access if Solo Doctor mode is active
        if ("RECEPTIONIST".equals(user.getRole()) && "SOLO".equals(settings.getReceptionMode())) {
            throw new RuntimeException("Solo Doctor Mode is active. Access restricted.");
        }

        response.setHospitalId(user.getHospitalId());
        response.setHospitalName(hospital.getName());
        response.setModules(hospital.getModules());
        response.setReceptionMode(settings.getReceptionMode());
        response.setBillingHandler(settings.getBillingHandler());
        response.setIsSingleDoctor(hospital.getIsSingleDoctor());
        response.setInClinic(settings.getInClinic());
        response.setLogoUrl(hospital.getLogoUrl());
        response.setParentOrganization(hospital.getParentOrganization());
        response.setHospitalAddress(hospital.getAddress());
        response.setHospitalPhone(hospital.getPhone());

        // Populate profile details
        populateProfileDetails(user, response);

        return response;
    }

    /**
     * Update current user profile details
     */
    @Transactional
    public LoginResponse updateProfile(String email, ProfileUpdateRequest request) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));

        // Password change handling
        if (request.getNewPassword() != null && !request.getNewPassword().trim().isEmpty()) {
            if (request.getCurrentPassword() == null || request.getCurrentPassword().trim().isEmpty()) {
                throw new RuntimeException("Current password is required to change password");
            }
            if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPassword())) {
                throw new RuntimeException("Incorrect current password");
            }
            user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        }

        // Name handling
        if (request.getName() != null && !request.getName().trim().isEmpty()) {
            user.setName(request.getName());
        }
        userRepository.save(user);

        // Update profile in respective detailed actor tables
        if (!"SUPER_ADMIN".equals(user.getRole())) {
            if ("HOSPITAL_ADMIN".equals(user.getRole())) {
                HospitalAdmin admin = hospitalAdminRepository.findByEmail(user.getEmail())
                        .orElseGet(() -> {
                            HospitalAdmin newAdmin = new HospitalAdmin();
                            newAdmin.setHospitalId(user.getHospitalId());
                            newAdmin.setEmail(user.getEmail());
                            newAdmin.setIsActive(true);
                            return newAdmin;
                        });
                admin.setName(user.getName());
                admin.setPhone(request.getPhone());
                admin.setAge(request.getAge());
                admin.setGender(request.getGender());
                hospitalAdminRepository.save(admin);

                Hospital hospital = hospitalRepository.findById(user.getHospitalId()).orElse(null);
                if (hospital != null) {
                    if (request.getHospitalName() != null && !request.getHospitalName().trim().isEmpty()) {
                        hospital.setName(request.getHospitalName());
                    }
                    if (request.getHospitalAddress() != null) {
                        hospital.setAddress(request.getHospitalAddress());
                    }
                    if (request.getHospitalPhone() != null) {
                        hospital.setPhone(request.getHospitalPhone());
                    }
                    if (request.getParentOrganization() != null) {
                        hospital.setParentOrganization(request.getParentOrganization());
                    }
                    if (request.getLogoUrl() != null) {
                        hospital.setLogoUrl(request.getLogoUrl());
                    }
                    hospitalRepository.save(hospital);
                }

                // Sync name, phone, and specialization to Doctor profile if single doctor mode is enabled
                if (hospital != null && Boolean.TRUE.equals(hospital.getIsSingleDoctor())) {
                    doctorRepository.findByEmailAndHospitalId(user.getEmail(), user.getHospitalId())
                            .ifPresent(doctor -> {
                                doctor.setName(user.getName());
                                doctor.setPhone(request.getPhone() != null ? request.getPhone() : "");
                                if (request.getSpecialization() != null && !request.getSpecialization().trim().isEmpty()) {
                                    doctor.setSpecialization(request.getSpecialization());
                                }
                                doctorRepository.save(doctor);
                            });
                }
            } else if ("RECEPTIONIST".equals(user.getRole())) {
                Receptionist receptionist = receptionistProfileRepository.findByEmail(user.getEmail())
                        .orElseGet(() -> {
                            Receptionist newRec = new Receptionist();
                            newRec.setHospitalId(user.getHospitalId());
                            newRec.setEmail(user.getEmail());
                            newRec.setIsActive(true);
                            return newRec;
                        });
                receptionist.setName(user.getName());
                receptionist.setPhone(request.getPhone());
                receptionist.setAge(request.getAge());
                receptionist.setGender(request.getGender());
                receptionistProfileRepository.save(receptionist);
            } else if ("PHARMACIST".equals(user.getRole())) {
                Pharmacist pharmacist = pharmacistProfileRepository.findByEmail(user.getEmail())
                        .orElseGet(() -> {
                            Pharmacist newPhar = new Pharmacist();
                            newPhar.setHospitalId(user.getHospitalId());
                            newPhar.setEmail(user.getEmail());
                            newPhar.setIsActive(true);
                            return newPhar;
                        });
                pharmacist.setName(user.getName());
                pharmacist.setPhone(request.getPhone());
                pharmacist.setAge(request.getAge());
                pharmacist.setGender(request.getGender());
                pharmacistProfileRepository.save(pharmacist);
            } else if ("DOCTOR".equals(user.getRole())) {
                Doctor doctor = doctorRepository.findByEmailAndHospitalId(user.getEmail(), user.getHospitalId())
                        .orElseThrow(() -> new RuntimeException("Doctor profile not found"));
                doctor.setName(user.getName());
                doctor.setPhone(request.getPhone());
                if (request.getSpecialization() != null && !request.getSpecialization().trim().isEmpty()) {
                    doctor.setSpecialization(request.getSpecialization());
                }
                doctorRepository.save(doctor);
            }
        }

        return getProfile(email);
    }

    /**
     * Get hospital fees (consultation and case paper) for the hospital of the
     * authenticated user.
     */
    public com.hms.dto.HospitalFeesDTO getHospitalFees(String email) {
        User user = userRepository.findByEmail(email).orElseThrow(() -> new RuntimeException("User not found"));
        if (user.getHospitalId() == null) throw new RuntimeException("Invalid hospital user");
        Hospital hospital = hospitalRepository.findById(user.getHospitalId()).orElseThrow(() -> new RuntimeException("Hospital not found"));
        com.hms.dto.HospitalFeesDTO dto = new com.hms.dto.HospitalFeesDTO();
        dto.setConsultationFee(hospital.getConsultationFee());
        dto.setCasePaperFee(hospital.getCasePaperFee());
        return dto;
    }

    /**
     * Update hospital fees. Only `HOSPITAL_ADMIN` role is allowed to update.
     */
    public com.hms.dto.HospitalFeesDTO updateHospitalFees(String email, com.hms.dto.HospitalFeesDTO fees) {
        User user = userRepository.findByEmail(email).orElseThrow(() -> new RuntimeException("User not found"));
        if (user.getHospitalId() == null) throw new RuntimeException("Invalid hospital user");
        if (!"HOSPITAL_ADMIN".equals(user.getRole())) {
            throw new RuntimeException("Access denied: requires HOSPITAL_ADMIN role");
        }
        Hospital hospital = hospitalRepository.findById(user.getHospitalId()).orElseThrow(() -> new RuntimeException("Hospital not found"));
        hospital.setConsultationFee(fees.getConsultationFee());
        hospital.setCasePaperFee(fees.getCasePaperFee());
        hospitalRepository.save(hospital);
        com.hms.dto.HospitalFeesDTO dto = new com.hms.dto.HospitalFeesDTO(hospital.getConsultationFee(), hospital.getCasePaperFee());
        return dto;
    }

    /**
     * Get operational settings (receptionMode and billingHandler) for the hospital of the authenticated user.
     */
    public HospitalSettingDTO getHospitalOperationsSettings(String email) {
        User user = userRepository.findByEmail(email).orElseThrow(() -> new RuntimeException("User not found"));
        if (user.getHospitalId() == null) throw new RuntimeException("Invalid hospital user");
        return hospitalSettingRepository.findByHospital_Id(user.getHospitalId())
                .map(s -> new HospitalSettingDTO(s.getReceptionMode(), s.getBillingHandler(), s.getInClinic()))
                .orElse(new HospitalSettingDTO("HAS_RECEPTIONIST", "RECEPTIONIST", true));
    }

    /**
     * Update operational settings. Only `HOSPITAL_ADMIN` role is allowed to update.
     */
    @Transactional
    public HospitalSettingDTO updateHospitalOperationsSettings(String email, HospitalSettingDTO dto) {
        User user = userRepository.findByEmail(email).orElseThrow(() -> new RuntimeException("User not found"));
        if (user.getHospitalId() == null) throw new RuntimeException("Invalid hospital user");
        if (!"HOSPITAL_ADMIN".equals(user.getRole())) {
            throw new RuntimeException("Access denied: requires HOSPITAL_ADMIN role");
        }

        // Normalize: trim whitespace and uppercase to be tolerant of minor client variations
        String receptionMode = dto.getReceptionMode() == null ? null : dto.getReceptionMode().trim().toUpperCase();
        String billingHandler = dto.getBillingHandler() == null ? null : dto.getBillingHandler().trim().toUpperCase();

        // Guard: validate normalized values against allowed domains
        if (!"HAS_RECEPTIONIST".equals(receptionMode) && !"SOLO".equals(receptionMode)) {
            throw new IllegalArgumentException("receptionMode must be HAS_RECEPTIONIST or SOLO");
        }
        if (!"RECEPTIONIST".equals(billingHandler) && !"DOCTOR".equals(billingHandler) && !"BOTH".equals(billingHandler)) {
            throw new IllegalArgumentException("billingHandler must be RECEPTIONIST, DOCTOR, or BOTH");
        }

        // Cross-field invariant: SOLO mode has no receptionist, so billing must be handled by DOCTOR.
        // Override any conflicting client-supplied billingHandler when switching to SOLO.
        if ("SOLO".equals(receptionMode)) {
            billingHandler = "DOCTOR";
        }

        final String effectiveBillingHandler = billingHandler;
        Boolean inClinic = dto.getInClinic();

        // Ensure a settings row exists; if not, create one first
        HospitalSetting settings = hospitalSettingRepository.findByHospital_Id(user.getHospitalId())
                .orElseGet(() -> {
                    Hospital hospital = hospitalRepository.findById(user.getHospitalId())
                            .orElseThrow(() -> new RuntimeException("Hospital not found"));
                    HospitalSetting newSettings = new HospitalSetting();
                    newSettings.setHospital(hospital);
                    newSettings.setReceptionMode(receptionMode != null ? receptionMode : "HAS_RECEPTIONIST");
                    newSettings.setBillingHandler(effectiveBillingHandler != null ? effectiveBillingHandler : "RECEPTIONIST");
                    newSettings.setInClinic(inClinic != null ? inClinic : Boolean.TRUE);
                    return hospitalSettingRepository.save(newSettings);
                });

        // Use a direct JPQL UPDATE — never touches hospital_id, eliminates detached-proxy issues
        hospitalSettingRepository.updateByHospitalId(
                user.getHospitalId(),
                receptionMode,
                billingHandler,
                inClinic != null ? inClinic : settings.getInClinic()
        );

        return new HospitalSettingDTO(receptionMode, billingHandler,
                inClinic != null ? inClinic : settings.getInClinic());
    }
}
