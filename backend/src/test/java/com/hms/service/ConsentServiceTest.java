package com.hms.service;

import com.hms.entity.*;
import com.hms.exception.ResourceNotFoundException;
import com.hms.exception.UnauthorizedException;
import com.hms.repository.*;
import com.hms.security.SecurityContextHelper;
import com.hms.service.hospital.ConsentService;
import com.hms.service.hospital.NotificationService;
import com.hms.service.hospital.SignatureAndDocumentService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ConsentServiceTest {

    @Mock private PatientConsentRepository patientConsentRepository;
    @Mock private BloodConsentDetailRepository bloodConsentDetailRepository;
    @Mock private PatientRepository patientRepository;
    @Mock private IpdAdmissionRepository ipdAdmissionRepository;
    @Mock private DoctorOrderRepository doctorOrderRepository;
    @Mock private SecurityContextHelper securityHelper;
    @Mock private SignatureAndDocumentService signatureDocumentService;
    @Mock private NotificationService notificationService;

    @InjectMocks private ConsentService consentService;

    @Test
    void createConsentDraft_successForGeneral() {
        when(securityHelper.getCurrentHospitalId()).thenReturn(1L);
        when(securityHelper.getCurrentUserId()).thenReturn(10L);

        Patient patient = new Patient();
        patient.setId(100L);
        patient.setHospitalId(1L);
        patient.setIsActive(true);

        IpdAdmission admission = new IpdAdmission();
        admission.setId(200L);
        admission.setHospitalId(1L);
        admission.setPatientId(100L);

        when(patientRepository.findByIdAndHospitalIdAndIsActiveTrue(100L, 1L)).thenReturn(Optional.of(patient));
        when(ipdAdmissionRepository.findById(200L)).thenReturn(Optional.of(admission));

        // No existing consent of this type
        when(patientConsentRepository.findByHospitalIdAndAdmissionIdAndIsDeletedFalse(1L, 200L))
                .thenReturn(new ArrayList<>());

        PatientConsent saved = new PatientConsent();
        saved.setId(500L);
        saved.setHospitalId(1L);
        saved.setPatientId(100L);
        saved.setAdmissionId(200L);
        saved.setConsentType("GENERAL");
        saved.setStatus("DRAFT");

        when(patientConsentRepository.save(any(PatientConsent.class))).thenReturn(saved);

        PatientConsent result = consentService.createConsentDraft(100L, 200L, "IPD", "GENERAL", "English", null);

        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(500L);
        assertThat(result.getStatus()).isEqualTo("DRAFT");
        verify(patientConsentRepository).save(any(PatientConsent.class));
    }

    @Test
    void createConsentDraft_crossTenantAccess_throwsUnauthorized() {
        when(securityHelper.getCurrentHospitalId()).thenReturn(1L);

        // Patient belongs to different hospital
        when(patientRepository.findByIdAndHospitalIdAndIsActiveTrue(100L, 1L))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> consentService.createConsentDraft(100L, 200L, "IPD", "GENERAL", "English", null))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void createConsentDraft_bloodWithoutOrder_throwsException() {
        when(securityHelper.getCurrentHospitalId()).thenReturn(1L);

        Patient patient = new Patient();
        patient.setId(100L);
        patient.setHospitalId(1L);
        patient.setIsActive(true);

        IpdAdmission admission = new IpdAdmission();
        admission.setId(200L);
        admission.setHospitalId(1L);
        admission.setPatientId(100L);

        when(patientRepository.findByIdAndHospitalIdAndIsActiveTrue(100L, 1L)).thenReturn(Optional.of(patient));
        when(ipdAdmissionRepository.findById(200L)).thenReturn(Optional.of(admission));
        // No active transfusion orders
        when(doctorOrderRepository.findByIpdAdmissionIdAndStatus(200L, "ACTIVE"))
                .thenReturn(new ArrayList<>());

        assertThatThrownBy(() -> consentService.createConsentDraft(100L, 200L, "IPD", "BLOOD", "English", 300L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("No active transfusion doctor order found");
    }

    @Test
    void signConsent_minorSelfSigning_throwsException() {
        when(securityHelper.getCurrentHospitalId()).thenReturn(1L);

        PatientConsent consent = new PatientConsent();
        consent.setId(500L);
        consent.setHospitalId(1L);
        consent.setPatientId(100L);
        consent.setStatus("DRAFT");

        Patient patient = new Patient();
        patient.setId(100L);
        patient.setHospitalId(1L);
        patient.setAge(15); // Underage

        when(patientConsentRepository.findById(500L)).thenReturn(Optional.of(consent));
        when(patientRepository.findByIdAndHospitalIdAndIsActiveTrue(100L, 1L)).thenReturn(Optional.of(patient));

        assertThatThrownBy(() -> consentService.signConsent(500L, "PATIENT", "Minor Child", null, "STYLUS", "base64"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Minors cannot sign consents themselves");
    }

    @Test
    void submitConsent_requiresExplanationAndWitnessesForBlood() {
        when(securityHelper.getCurrentHospitalId()).thenReturn(1L);

        PatientConsent consent = new PatientConsent();
        consent.setId(500L);
        consent.setHospitalId(1L);
        consent.setConsentType("BLOOD");
        consent.setPatientSigned(true);
        consent.setStatus("SIGNED");

        BloodConsentDetail detail = new BloodConsentDetail();
        detail.setConsentId(500L);
        detail.setExplanationGiven(false); // Acknowledge explanation missing

        when(patientConsentRepository.findById(500L)).thenReturn(Optional.of(consent));
        when(bloodConsentDetailRepository.findByConsentId(500L)).thenReturn(Optional.of(detail));

        assertThatThrownBy(() -> consentService.submitConsent(500L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("explanation must be acknowledged");
    }

    @Test
    void submitConsent_locksConsentAndTriggersNotification() {
        when(securityHelper.getCurrentHospitalId()).thenReturn(1L);

        PatientConsent consent = new PatientConsent();
        consent.setId(500L);
        consent.setHospitalId(1L);
        consent.setConsentType("GENERAL");
        consent.setPatientSigned(true);
        consent.setStatus("SIGNED");

        when(patientConsentRepository.findById(500L)).thenReturn(Optional.of(consent));
        when(patientConsentRepository.save(any(PatientConsent.class))).thenAnswer(invocation -> invocation.getArgument(0));

        PatientConsent result = consentService.submitConsent(500L);

        assertThat(result.getStatus()).isEqualTo("LOCKED");
        verify(notificationService).sendWebSocketRefresh(1L, "PATIENT_CONSENT_LOCKED", 500L);
    }
}
