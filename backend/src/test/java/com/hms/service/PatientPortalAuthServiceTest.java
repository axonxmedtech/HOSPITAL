package com.hms.service;

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
import com.hms.service.portal.PatientPortalAuthService;
import com.hms.service.portal.SmsGateway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PatientPortalAuthServiceTest {

    @Mock private PatientRepository patientRepository;
    @Mock private PatientPortalUserRepository portalUserRepository;
    @Mock private PortalOtpRepository otpRepository;
    @Mock private SmsGateway smsGateway;
    @Mock private JwtUtil jwtUtil;

    @InjectMocks
    private PatientPortalAuthService service;

    private static final Long HOSPITAL_ID = 1L;

    private Patient patient(Long id, String customId) {
        Patient p = new Patient();
        p.setId(id);
        p.setHospitalId(HOSPITAL_ID);
        p.setName("Jane Doe");
        p.setPhone("9876543210");
        p.setCustomId(customId);
        return p;
    }

    // ===== OTP request =====

    @Test
    void requestOtp_noMatchingPatient_silentlyNoOpsWithoutRevealingNonExistence() {
        when(patientRepository.findByPhoneAndHospitalIdAndIsActiveTrue("9876543210", HOSPITAL_ID))
                .thenReturn(List.of());

        PortalOtpRequestRequest req = new PortalOtpRequestRequest();
        req.setMobile("9876543210");

        // Must NOT throw — an anonymous caller shouldn't be able to distinguish
        // "no such patient" from "OTP sent" (patient enumeration defense).
        service.requestOtp(HOSPITAL_ID, req);

        verify(smsGateway, never()).send(any(), any());
        verify(portalUserRepository, never()).save(any());
    }

    @Test
    void requestOtp_ambiguousMatchWithoutUhid_throws() {
        when(patientRepository.findByPhoneAndHospitalIdAndIsActiveTrue("9876543210", HOSPITAL_ID))
                .thenReturn(List.of(patient(1L, "UHID-1"), patient(2L, "UHID-2")));

        PortalOtpRequestRequest req = new PortalOtpRequestRequest();
        req.setMobile("9876543210");

        assertThatThrownBy(() -> service.requestOtp(HOSPITAL_ID, req))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Multiple records found");
    }

    @Test
    void requestOtp_ambiguousMatchWithUhid_resolvesAndSendsOtp() {
        when(patientRepository.findByPhoneAndHospitalIdAndIsActiveTrue("9876543210", HOSPITAL_ID))
                .thenReturn(List.of(patient(1L, "UHID-1"), patient(2L, "UHID-2")));
        when(portalUserRepository.findByHospitalIdAndPatientId(HOSPITAL_ID, 2L)).thenReturn(Optional.empty());
        when(portalUserRepository.save(any(PatientPortalUser.class))).thenAnswer(i -> i.getArgument(0));
        when(otpRepository.save(any(PortalOtp.class))).thenAnswer(i -> i.getArgument(0));

        PortalOtpRequestRequest req = new PortalOtpRequestRequest();
        req.setMobile("9876543210");
        req.setUhid("UHID-2");

        service.requestOtp(HOSPITAL_ID, req);

        verify(smsGateway).send(eq("9876543210"), any());
        verify(otpRepository).save(any(PortalOtp.class));
    }

    @Test
    void requestOtp_singleMatch_createsPortalUserAndSendsOtp() {
        when(patientRepository.findByPhoneAndHospitalIdAndIsActiveTrue("9876543210", HOSPITAL_ID))
                .thenReturn(List.of(patient(1L, "UHID-1")));
        when(portalUserRepository.findByHospitalIdAndPatientId(HOSPITAL_ID, 1L)).thenReturn(Optional.empty());
        when(portalUserRepository.save(any(PatientPortalUser.class))).thenAnswer(i -> i.getArgument(0));
        when(otpRepository.save(any(PortalOtp.class))).thenAnswer(i -> i.getArgument(0));

        PortalOtpRequestRequest req = new PortalOtpRequestRequest();
        req.setMobile("9876543210");

        service.requestOtp(HOSPITAL_ID, req);

        verify(portalUserRepository).save(any(PatientPortalUser.class));
        verify(smsGateway).send(eq("9876543210"), any());
    }

    @Test
    void requestOtp_lockedAccountWithinCooldown_throws() {
        when(patientRepository.findByPhoneAndHospitalIdAndIsActiveTrue("9876543210", HOSPITAL_ID))
                .thenReturn(List.of(patient(1L, "UHID-1")));
        PatientPortalUser locked = new PatientPortalUser();
        locked.setId(5L);
        locked.setStatus("LOCKED");
        locked.setLockUntil(LocalDateTime.now().plusMinutes(10));
        when(portalUserRepository.findByHospitalIdAndPatientId(HOSPITAL_ID, 1L)).thenReturn(Optional.of(locked));

        PortalOtpRequestRequest req = new PortalOtpRequestRequest();
        req.setMobile("9876543210");

        assertThatThrownBy(() -> service.requestOtp(HOSPITAL_ID, req))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("locked");
        verify(smsGateway, never()).send(any(), any());
    }

    // ===== OTP verify =====

    @Test
    void verifyOtp_noOtpOnFile_throws() {
        when(otpRepository.findTopByHospitalIdAndMobileAndConsumedAtIsNullOrderByCreatedAtDesc(HOSPITAL_ID, "9876543210"))
                .thenReturn(Optional.empty());

        PortalOtpVerifyRequest req = new PortalOtpVerifyRequest();
        req.setMobile("9876543210");
        req.setOtp("123456");

        assertThatThrownBy(() -> service.verifyOtp(HOSPITAL_ID, req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid or expired");
    }

    @Test
    void verifyOtp_expiredOtp_throws() {
        PortalOtp otp = new PortalOtp();
        otp.setId(9L);
        otp.setMobile("9876543210");
        otp.setOtpHash(service.hashOtp("123456"));
        otp.setExpiresAt(LocalDateTime.now().minusMinutes(1));
        when(otpRepository.findTopByHospitalIdAndMobileAndConsumedAtIsNullOrderByCreatedAtDesc(HOSPITAL_ID, "9876543210"))
                .thenReturn(Optional.of(otp));

        PortalOtpVerifyRequest req = new PortalOtpVerifyRequest();
        req.setMobile("9876543210");
        req.setOtp("123456");

        assertThatThrownBy(() -> service.verifyOtp(HOSPITAL_ID, req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid or expired");
    }

    @Test
    void verifyOtp_wrongCode_incrementsAttemptsAndLocksAfterFive() {
        PortalOtp otp = new PortalOtp();
        otp.setId(9L);
        otp.setMobile("9876543210");
        otp.setOtpHash(service.hashOtp("123456"));
        otp.setExpiresAt(LocalDateTime.now().plusMinutes(5));
        otp.setAttemptCount(4);
        when(otpRepository.findTopByHospitalIdAndMobileAndConsumedAtIsNullOrderByCreatedAtDesc(HOSPITAL_ID, "9876543210"))
                .thenReturn(Optional.of(otp));
        when(portalUserRepository.findByHospitalIdAndMobile(HOSPITAL_ID, "9876543210")).thenReturn(List.of());
        when(otpRepository.save(any(PortalOtp.class))).thenAnswer(i -> i.getArgument(0));

        PortalOtpVerifyRequest req = new PortalOtpVerifyRequest();
        req.setMobile("9876543210");
        req.setOtp("000000");

        assertThatThrownBy(() -> service.verifyOtp(HOSPITAL_ID, req))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(otp.getAttemptCount()).isEqualTo(5);
    }

    @Test
    void verifyOtp_rejectedImmediatelyWhenAccountAlreadyLocked() {
        PatientPortalUser locked = new PatientPortalUser();
        locked.setId(5L);
        locked.setStatus("LOCKED");
        locked.setLockUntil(LocalDateTime.now().plusMinutes(10));
        when(portalUserRepository.findByHospitalIdAndMobile(HOSPITAL_ID, "9876543210"))
                .thenReturn(List.of(locked));

        PortalOtpVerifyRequest req = new PortalOtpVerifyRequest();
        req.setMobile("9876543210");
        req.setOtp("123456");

        assertThatThrownBy(() -> service.verifyOtp(HOSPITAL_ID, req))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("locked");
        verify(otpRepository, never()).findTopByHospitalIdAndMobileAndConsumedAtIsNullOrderByCreatedAtDesc(any(), any());
    }

    @Test
    void verifyOtp_wrongCode_locksAllAccountsSharingTheMobile() {
        PortalOtp otp = new PortalOtp();
        otp.setId(9L);
        otp.setMobile("9876543210");
        otp.setOtpHash(service.hashOtp("123456"));
        otp.setExpiresAt(LocalDateTime.now().plusMinutes(5));
        otp.setAttemptCount(4);
        when(otpRepository.findTopByHospitalIdAndMobileAndConsumedAtIsNullOrderByCreatedAtDesc(HOSPITAL_ID, "9876543210"))
                .thenReturn(Optional.of(otp));
        when(otpRepository.save(any(PortalOtp.class))).thenAnswer(i -> i.getArgument(0));

        PatientPortalUser userA = new PatientPortalUser();
        userA.setId(7L);
        userA.setStatus("ACTIVE");
        PatientPortalUser userB = new PatientPortalUser();
        userB.setId(8L);
        userB.setStatus("ACTIVE");
        when(portalUserRepository.findByHospitalIdAndMobile(HOSPITAL_ID, "9876543210"))
                .thenReturn(List.of(userA, userB));
        when(portalUserRepository.save(any(PatientPortalUser.class))).thenAnswer(i -> i.getArgument(0));

        PortalOtpVerifyRequest req = new PortalOtpVerifyRequest();
        req.setMobile("9876543210");
        req.setOtp("000000");

        assertThatThrownBy(() -> service.verifyOtp(HOSPITAL_ID, req))
                .isInstanceOf(IllegalArgumentException.class);

        assertThat(userA.getStatus()).isEqualTo("LOCKED");
        assertThat(userB.getStatus()).isEqualTo("LOCKED");
    }

    @Test
    void verifyOtp_correctCode_mintsTokenAndConsumesOtp() {
        PortalOtp otp = new PortalOtp();
        otp.setId(9L);
        otp.setHospitalId(HOSPITAL_ID);
        otp.setMobile("9876543210");
        otp.setOtpHash(service.hashOtp("123456"));
        otp.setExpiresAt(LocalDateTime.now().plusMinutes(5));
        otp.setAttemptCount(0);
        when(otpRepository.findTopByHospitalIdAndMobileAndConsumedAtIsNullOrderByCreatedAtDesc(HOSPITAL_ID, "9876543210"))
                .thenReturn(Optional.of(otp));
        when(otpRepository.save(any(PortalOtp.class))).thenAnswer(i -> i.getArgument(0));

        Patient p = patient(1L, "UHID-1");
        when(patientRepository.findByPhoneAndHospitalIdAndIsActiveTrue("9876543210", HOSPITAL_ID))
                .thenReturn(List.of(p));

        PatientPortalUser portalUser = new PatientPortalUser();
        portalUser.setId(5L);
        portalUser.setHospitalId(HOSPITAL_ID);
        portalUser.setPatientId(1L);
        portalUser.setStatus("ACTIVE");
        when(portalUserRepository.findByHospitalIdAndPatientId(HOSPITAL_ID, 1L)).thenReturn(Optional.of(portalUser));
        when(portalUserRepository.save(any(PatientPortalUser.class))).thenAnswer(i -> i.getArgument(0));
        when(jwtUtil.generateToken(5L, "9876543210", "PATIENT", HOSPITAL_ID, List.of())).thenReturn("fake-jwt");

        PortalOtpVerifyRequest req = new PortalOtpVerifyRequest();
        req.setMobile("9876543210");
        req.setOtp("123456");

        PortalLoginResponse resp = service.verifyOtp(HOSPITAL_ID, req);

        assertThat(resp.getToken()).isEqualTo("fake-jwt");
        assertThat(resp.getPatientId()).isEqualTo(1L);
        assertThat(otp.getConsumedAt()).isNotNull();
        assertThat(portalUser.getLastLogin()).isNotNull();
    }
}
