package com.hms.service.portal;

import com.hms.dto.PortalLoginResponse;
import com.hms.dto.PortalOtpRequestRequest;
import com.hms.dto.PortalOtpVerifyRequest;
import com.hms.entity.Patient;
import com.hms.entity.PatientPortalUser;
import com.hms.entity.PortalOtp;
import com.hms.repository.PatientPortalUserRepository;
import com.hms.repository.PatientRepository;
import com.hms.repository.PortalOtpRepository;
import com.hms.security.JwtUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;

/**
 * Patient portal OTP login (Form 40 phase 1). Two endpoints cover both first-time
 * registration and every subsequent login — there is no password step.
 */
@Service
public class PatientPortalAuthService {

    private static final int OTP_VALIDITY_MINUTES = 5;
    private static final int MAX_ATTEMPTS = 5;
    private static final int LOCK_MINUTES = 15;
    @Value("${portal.otp.hmac-key}")
    private String otpHmacKey = "patient-portal-otp-hmac";

    @Autowired private PatientRepository patientRepository;
    @Autowired private PatientPortalUserRepository portalUserRepository;
    @Autowired private PortalOtpRepository otpRepository;
    @Autowired private SmsGateway smsGateway;
    @Autowired private JwtUtil jwtUtil;

    @Transactional
    public void requestOtp(Long hospitalId, PortalOtpRequestRequest request) {
        Patient patient = resolvePatient(hospitalId, request.getMobile(), request.getUhid());

        PatientPortalUser portalUser = portalUserRepository
                .findByHospitalIdAndPatientId(hospitalId, patient.getId())
                .orElseGet(() -> {
                    PatientPortalUser u = new PatientPortalUser();
                    u.setHospitalId(hospitalId);
                    u.setPatientId(patient.getId());
                    u.setMobile(request.getMobile());
                    u.setStatus("ACTIVE");
                    return portalUserRepository.save(u);
                });

        if ("LOCKED".equals(portalUser.getStatus()) && portalUser.getLockUntil() != null
                && portalUser.getLockUntil().isAfter(LocalDateTime.now())) {
            throw new IllegalStateException("This account is temporarily locked due to repeated failed attempts. Try again later.");
        }

        String otp = generateOtp();
        PortalOtp record = new PortalOtp();
        record.setHospitalId(hospitalId);
        record.setMobile(request.getMobile());
        record.setOtpHash(hashOtp(otp));
        record.setPurpose("LOGIN");
        record.setExpiresAt(LocalDateTime.now().plusMinutes(OTP_VALIDITY_MINUTES));
        record.setAttemptCount(0);
        otpRepository.save(record);

        smsGateway.send(request.getMobile(), otp);
    }

    @Transactional(noRollbackFor = {IllegalArgumentException.class, IllegalStateException.class})
    public PortalLoginResponse verifyOtp(Long hospitalId, PortalOtpVerifyRequest request) {
        List<PatientPortalUser> accountsForMobile = portalUserRepository.findByHospitalIdAndMobile(hospitalId, request.getMobile());
        boolean anyLocked = accountsForMobile.stream().anyMatch(u ->
                "LOCKED".equals(u.getStatus()) && u.getLockUntil() != null && u.getLockUntil().isAfter(LocalDateTime.now()));
        if (anyLocked) {
            throw new IllegalStateException("This account is temporarily locked due to repeated failed attempts. Try again later.");
        }

        PortalOtp otp = otpRepository
                .findTopByHospitalIdAndMobileAndConsumedAtIsNullOrderByCreatedAtDesc(hospitalId, request.getMobile())
                .orElseThrow(() -> new IllegalArgumentException("Invalid or expired OTP."));

        if (otp.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new IllegalArgumentException("Invalid or expired OTP.");
        }

        if (!constantTimeEquals(otp.getOtpHash(), hashOtp(request.getOtp()))) {
            otp.setAttemptCount(otp.getAttemptCount() + 1);
            otpRepository.save(otp);
            if (otp.getAttemptCount() >= MAX_ATTEMPTS) {
                for (PatientPortalUser u : accountsForMobile) {
                    u.setStatus("LOCKED");
                    u.setLockUntil(LocalDateTime.now().plusMinutes(LOCK_MINUTES));
                    portalUserRepository.save(u);
                }
            }
            throw new IllegalArgumentException("Invalid or expired OTP.");
        }

        otp.setConsumedAt(LocalDateTime.now());
        otp.setAttemptCount(0);
        otpRepository.save(otp);

        Patient patient = resolvePatient(hospitalId, request.getMobile(), null);
        PatientPortalUser portalUser = portalUserRepository
                .findByHospitalIdAndPatientId(hospitalId, patient.getId())
                .orElseThrow(() -> new IllegalStateException("Portal account not found."));
        portalUser.setStatus("ACTIVE");
        portalUser.setLockUntil(null);
        portalUser.setLastLogin(LocalDateTime.now());
        portalUserRepository.save(portalUser);

        String token = jwtUtil.generateToken(portalUser.getId(), request.getMobile(), "PATIENT", hospitalId, List.of());
        return new PortalLoginResponse(token, patient.getId(), patient.getName(), patient.getCustomId());
    }

    private Patient resolvePatient(Long hospitalId, String mobile, String uhid) {
        List<Patient> matches = patientRepository.findByPhoneAndHospitalIdAndIsActiveTrue(mobile, hospitalId);
        if (matches.isEmpty()) {
            throw new RuntimeException("No patient record found with this number. Please visit reception to register.");
        }
        if (matches.size() == 1) {
            return matches.get(0);
        }
        if (uhid == null || uhid.isBlank()) {
            throw new IllegalStateException("Multiple records found. Please also provide your patient ID.");
        }
        return matches.stream()
                .filter(p -> uhid.equalsIgnoreCase(p.getCustomId()))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("No patient record found with this number and ID."));
    }

    private String generateOtp() {
        SecureRandom random = new SecureRandom();
        return String.format("%06d", random.nextInt(1_000_000));
    }

    public String hashOtp(String otp) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(otpHmacKey.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] hash = mac.doFinal(otp.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            throw new RuntimeException("Failed to hash OTP", e);
        }
    }

    private boolean constantTimeEquals(String a, String b) {
        return java.security.MessageDigest.isEqual(
                a.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                b.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }
}
