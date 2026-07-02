package com.hms.service;

import com.hms.dto.ConsentCreateRequest;
import com.hms.dto.ConsentSignRequest;
import com.hms.entity.*;
import com.hms.exception.ResourceNotFoundException;
import com.hms.exception.UnauthorizedException;
import com.hms.repository.*;
import com.hms.security.SecurityContextHelper;
import com.hms.service.hospital.ConsentService;
import com.hms.service.hospital.SignatureAndDocumentService;
import com.hms.service.hospital.NotificationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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

    @Mock
    private PatientConsentRepository patientConsentRepository;

    @Mock
    private BloodConsentDetailRepository bloodConsentDetailRepository;

    @Mock
    private SurgicalConsentDetailRepository surgicalConsentDetailRepository;

    @Mock
    private PatientRepository patientRepository;

    @Mock
    private IpdAdmissionRepository ipdAdmissionRepository;

    @Mock
    private DoctorOrderRepository doctorOrderRepository;

    @Mock
    private SecurityContextHelper securityHelper;

    @Mock
    private SignatureAndDocumentService signatureDocumentService;

    @Mock
    private NotificationService notificationService;

    @Mock
    private AuditLogRepository auditLogRepository;

    @InjectMocks
    private ConsentService consentService;

    @Test
    void createConsentDraft_Success_ForGeneralType() {
        // Arrange
        when(securityHelper.getCurrentHospitalId()).thenReturn(1L);
        when(securityHelper.getCurrentUserId()).thenReturn(2L);

        Patient patient = new Patient();
        patient.setId(10L);
        patient.setHospitalId(1L);
        patient.setName("John Doe");
        patient.setIsActive(true);
        when(patientRepository.findByIdAndHospitalIdAndIsActiveTrue(10L, 1L)).thenReturn(Optional.of(patient));

        IpdAdmission admission = new IpdAdmission();
        admission.setId(20L);
        admission.setHospitalId(1L);
        admission.setPatientId(10L);
        when(ipdAdmissionRepository.findById(20L)).thenReturn(Optional.of(admission));

        when(patientConsentRepository.findByHospitalIdAndAdmissionIdAndIsDeletedFalse(1L, 20L))
                .thenReturn(new ArrayList<>());

        ConsentCreateRequest request = new ConsentCreateRequest();
        request.setPatientId(10L);
        request.setAdmissionId(20L);
        request.setConsentType("GENERAL");
        request.setEncounterType("IPD");
        request.setLanguage("ENGLISH");

        when(patientConsentRepository.save(any(PatientConsent.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        PatientConsent result = consentService.createConsentDraft(request);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.getConsentType()).isEqualTo("GENERAL");
        assertThat(result.getStatus()).isEqualTo("DRAFT");
        assertThat(result.getPatientSigned()).isFalse();
        verify(patientConsentRepository).save(any(PatientConsent.class));
    }

    @Test
    void createConsentDraft_Success_WithPatientSigned() {
        // Arrange
        when(securityHelper.getCurrentHospitalId()).thenReturn(1L);
        when(securityHelper.getCurrentUserId()).thenReturn(2L);

        Patient patient = new Patient();
        patient.setId(10L);
        patient.setHospitalId(1L);
        patient.setName("John Doe");
        patient.setIsActive(true);
        when(patientRepository.findByIdAndHospitalIdAndIsActiveTrue(10L, 1L)).thenReturn(Optional.of(patient));

        IpdAdmission admission = new IpdAdmission();
        admission.setId(20L);
        admission.setHospitalId(1L);
        admission.setPatientId(10L);
        when(ipdAdmissionRepository.findById(20L)).thenReturn(Optional.of(admission));

        when(patientConsentRepository.findByHospitalIdAndAdmissionIdAndIsDeletedFalse(1L, 20L))
                .thenReturn(new ArrayList<>());

        ConsentCreateRequest request = new ConsentCreateRequest();
        request.setPatientId(10L);
        request.setAdmissionId(20L);
        request.setConsentType("GENERAL");
        request.setEncounterType("IPD");
        request.setLanguage("ENGLISH");
        request.setPatientSigned(true);
        request.setSignatureType("WET");

        when(patientConsentRepository.save(any(PatientConsent.class))).thenAnswer(invocation -> {
            PatientConsent pc = invocation.getArgument(0);
            pc.setId(99L);
            return pc;
        });

        // Act
        PatientConsent result = consentService.createConsentDraft(request);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.getStatus()).isEqualTo("SIGNED");
        assertThat(result.getPatientSigned()).isTrue();
        verify(signatureDocumentService).saveSignatureSlot(any(SignatureSlot.class));
    }

    @Test
    void createConsentDraft_TenantMismatch_ThrowsUnauthorizedException() {
        // Arrange
        when(securityHelper.getCurrentHospitalId()).thenReturn(1L);

        // Patient is in hospital 2
        when(patientRepository.findByIdAndHospitalIdAndIsActiveTrue(10L, 1L)).thenReturn(Optional.empty());

        ConsentCreateRequest request = new ConsentCreateRequest();
        request.setPatientId(10L);
        request.setConsentType("GENERAL");

        // Act & Assert
        assertThatThrownBy(() -> consentService.createConsentDraft(request))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void createConsentDraft_DuplicateActiveConsent_ThrowsIllegalArgumentException() {
        // Arrange
        when(securityHelper.getCurrentHospitalId()).thenReturn(1L);

        Patient patient = new Patient();
        patient.setId(10L);
        patient.setHospitalId(1L);
        patient.setIsActive(true);
        when(patientRepository.findByIdAndHospitalIdAndIsActiveTrue(10L, 1L)).thenReturn(Optional.of(patient));

        IpdAdmission admission = new IpdAdmission();
        admission.setId(20L);
        admission.setHospitalId(1L);
        admission.setPatientId(10L);
        when(ipdAdmissionRepository.findById(20L)).thenReturn(Optional.of(admission));

        PatientConsent existing = new PatientConsent();
        existing.setConsentType("GENERAL");
        existing.setStatus("DRAFT");

        when(patientConsentRepository.findByHospitalIdAndAdmissionIdAndIsDeletedFalse(1L, 20L))
                .thenReturn(Collections.singletonList(existing));

        ConsentCreateRequest request = new ConsentCreateRequest();
        request.setPatientId(10L);
        request.setAdmissionId(20L);
        request.setConsentType("GENERAL");

        // Act & Assert
        assertThatThrownBy(() -> consentService.createConsentDraft(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("An active consent of type GENERAL already exists");
    }

    @Test
    void createConsentDraft_BloodNoActiveTransfusionOrder_ThrowsIllegalArgumentException() {
        // Arrange
        when(securityHelper.getCurrentHospitalId()).thenReturn(1L);

        Patient patient = new Patient();
        patient.setId(10L);
        patient.setHospitalId(1L);
        patient.setIsActive(true);
        when(patientRepository.findByIdAndHospitalIdAndIsActiveTrue(10L, 1L)).thenReturn(Optional.of(patient));

        IpdAdmission admission = new IpdAdmission();
        admission.setId(20L);
        admission.setHospitalId(1L);
        admission.setPatientId(10L);
        when(ipdAdmissionRepository.findById(20L)).thenReturn(Optional.of(admission));

        // No active doctor orders
        when(doctorOrderRepository.findByIpdAdmissionIdAndStatus(20L, "ACTIVE")).thenReturn(new ArrayList<>());

        ConsentCreateRequest request = new ConsentCreateRequest();
        request.setPatientId(10L);
        request.setAdmissionId(20L);
        request.setConsentType("BLOOD");

        // Act & Assert
        assertThatThrownBy(() -> consentService.createConsentDraft(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("No active transfusion doctor order found");
    }

    @Test
    void signConsent_MinorCheck_FailsWhenMinorSigns() {
        // Arrange
        when(securityHelper.getCurrentHospitalId()).thenReturn(1L);

        PatientConsent consent = new PatientConsent();
        consent.setId(15L);
        consent.setHospitalId(1L);
        consent.setPatientId(10L);
        consent.setStatus("DRAFT");
        when(patientConsentRepository.findById(15L)).thenReturn(Optional.of(consent));

        Patient patient = new Patient();
        patient.setId(10L);
        patient.setHospitalId(1L);
        patient.setAge(15); // Under 18
        when(patientRepository.findByIdAndHospitalIdAndIsActiveTrue(10L, 1L)).thenReturn(Optional.of(patient));

        ConsentSignRequest signRequest = new ConsentSignRequest();
        signRequest.setPatientSigned(true);
        signRequest.setSignatureType("WET");

        // Act & Assert
        assertThatThrownBy(() -> consentService.signConsent(15L, signRequest))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Minors cannot sign consents themselves");
    }

    @Test
    void signConsent_MinorCheck_FailsWhenMinorSigns_BasedOnDOB() {
        // Arrange
        when(securityHelper.getCurrentHospitalId()).thenReturn(1L);

        PatientConsent consent = new PatientConsent();
        consent.setId(15L);
        consent.setHospitalId(1L);
        consent.setPatientId(10L);
        consent.setStatus("DRAFT");
        when(patientConsentRepository.findById(15L)).thenReturn(Optional.of(consent));

        Patient patient = new Patient();
        patient.setId(10L);
        patient.setHospitalId(1L);
        // DOB 5 years ago
        patient.setDateOfBirth(java.time.LocalDate.now().minusYears(5));
        when(patientRepository.findByIdAndHospitalIdAndIsActiveTrue(10L, 1L)).thenReturn(Optional.of(patient));

        ConsentSignRequest signRequest = new ConsentSignRequest();
        signRequest.setPatientSigned(true);
        signRequest.setSignatureType("WET");

        // Act & Assert
        assertThatThrownBy(() -> consentService.signConsent(15L, signRequest))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Minors cannot sign consents themselves");
    }

    @Test
    void signConsent_MinorCheck_SucceedsWhenAdultSigns_BasedOnDOB() {
        // Arrange
        when(securityHelper.getCurrentHospitalId()).thenReturn(1L);

        PatientConsent consent = new PatientConsent();
        consent.setId(15L);
        consent.setHospitalId(1L);
        consent.setPatientId(10L);
        consent.setStatus("DRAFT");
        when(patientConsentRepository.findById(15L)).thenReturn(Optional.of(consent));

        Patient patient = new Patient();
        patient.setId(10L);
        patient.setHospitalId(1L);
        // DOB 25 years ago
        patient.setDateOfBirth(java.time.LocalDate.now().minusYears(25));
        patient.setName("Jane Adult");
        when(patientRepository.findByIdAndHospitalIdAndIsActiveTrue(10L, 1L)).thenReturn(Optional.of(patient));

        ConsentSignRequest signRequest = new ConsentSignRequest();
        signRequest.setPatientSigned(true);
        signRequest.setSignatureType("WET");

        when(patientConsentRepository.save(any(PatientConsent.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        PatientConsent result = consentService.signConsent(15L, signRequest);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.getPatientSigned()).isTrue();
        assertThat(result.getStatus()).isEqualTo("SIGNED");
    }

    @Test
    void signConsent_Success_AsGuardianForMinor() {
        // Arrange
        when(securityHelper.getCurrentHospitalId()).thenReturn(1L);

        PatientConsent consent = new PatientConsent();
        consent.setId(15L);
        consent.setHospitalId(1L);
        consent.setPatientId(10L);
        consent.setStatus("DRAFT");
        consent.setConsentType("GENERAL");
        when(patientConsentRepository.findById(15L)).thenReturn(Optional.of(consent));

        Patient patient = new Patient();
        patient.setId(10L);
        patient.setHospitalId(1L);
        patient.setAge(15); // Minor
        patient.setName("Tiny Tim");
        patient.setGuardianName("Tim Senior");
        patient.setGuardianRelationship("Father");
        when(patientRepository.findByIdAndHospitalIdAndIsActiveTrue(10L, 1L)).thenReturn(Optional.of(patient));

        ConsentSignRequest signRequest = new ConsentSignRequest();
        signRequest.setGuardianSigned(true);
        signRequest.setRelationship("Father");
        signRequest.setSignatureType("WET");

        when(patientConsentRepository.save(any(PatientConsent.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        PatientConsent result = consentService.signConsent(15L, signRequest);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.getGuardianSigned()).isTrue();
        assertThat(result.getRelationship()).isEqualTo("Father");
        assertThat(result.getStatus()).isEqualTo("SIGNED");
        verify(signatureDocumentService).saveSignatureSlot(argThat(slot -> 
            "Tim Senior".equals(slot.getSignerName()) && "Father".equals(slot.getSignerRelationship())
        ));
    }

    @Test
    void signConsent_FailsOnRelationshipMismatch_ForGeneralMinor() {
        // Arrange
        when(securityHelper.getCurrentHospitalId()).thenReturn(1L);

        PatientConsent consent = new PatientConsent();
        consent.setId(15L);
        consent.setHospitalId(1L);
        consent.setPatientId(10L);
        consent.setStatus("DRAFT");
        consent.setConsentType("GENERAL");
        when(patientConsentRepository.findById(15L)).thenReturn(Optional.of(consent));

        Patient patient = new Patient();
        patient.setId(10L);
        patient.setHospitalId(1L);
        patient.setAge(15); // Minor
        patient.setName("Tiny Tim");
        patient.setGuardianName("Tim Senior");
        patient.setGuardianRelationship("Father");
        when(patientRepository.findByIdAndHospitalIdAndIsActiveTrue(10L, 1L)).thenReturn(Optional.of(patient));

        ConsentSignRequest signRequest = new ConsentSignRequest();
        signRequest.setGuardianSigned(true);
        signRequest.setRelationship("Uncle"); // Mismatch
        signRequest.setSignatureType("WET");

        // Act & Assert
        assertThatThrownBy(() -> consentService.signConsent(15L, signRequest))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Guardian relationship must match registered relationship");
    }

    @Test
    void signConsent_TenantMismatch_ThrowsUnauthorizedException() {
        // Arrange
        when(securityHelper.getCurrentHospitalId()).thenReturn(2L); // Different hospital

        PatientConsent consent = new PatientConsent();
        consent.setId(15L);
        consent.setHospitalId(1L); // Hospital 1
        when(patientConsentRepository.findById(15L)).thenReturn(Optional.of(consent));

        // Act & Assert
        assertThatThrownBy(() -> consentService.signConsent(15L, new ConsentSignRequest()))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void submitConsent_LocksConsent() {
        // Arrange
        when(securityHelper.getCurrentHospitalId()).thenReturn(1L);

        Patient patient = new Patient();
        patient.setId(10L);
        patient.setHospitalId(1L);
        patient.setAge(35); // adult
        when(patientRepository.findByIdAndHospitalIdAndIsActiveTrue(10L, 1L)).thenReturn(Optional.of(patient));

        PatientConsent consent = new PatientConsent();
        consent.setId(15L);
        consent.setHospitalId(1L);
        consent.setPatientId(10L);
        consent.setPatientSigned(true);
        consent.setConsentType("GENERAL");
        when(patientConsentRepository.findById(15L)).thenReturn(Optional.of(consent));
        when(patientConsentRepository.save(any(PatientConsent.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        PatientConsent result = consentService.submitConsent(15L);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.getStatus()).isEqualTo("LOCKED");
        verify(notificationService).sendWebSocketRefresh(eq(1L), eq("PATIENT_CONSENT_LOCKED"), eq(15L));
    }

    @Test
    void submitConsent_FailsIfNoSignature() {
        // Arrange
        when(securityHelper.getCurrentHospitalId()).thenReturn(1L);

        Patient patient = new Patient();
        patient.setId(10L);
        patient.setHospitalId(1L);
        patient.setAge(35); // adult
        when(patientRepository.findByIdAndHospitalIdAndIsActiveTrue(10L, 1L)).thenReturn(Optional.of(patient));

        PatientConsent consent = new PatientConsent();
        consent.setId(15L);
        consent.setHospitalId(1L);
        consent.setPatientId(10L);
        consent.setPatientSigned(false);
        consent.setGuardianSigned(false);
        consent.setConsentType("GENERAL");
        when(patientConsentRepository.findById(15L)).thenReturn(Optional.of(consent));

        // Act & Assert
        assertThatThrownBy(() -> consentService.submitConsent(15L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Either Patient or Guardian signature must be captured");
    }

    @Test
    void submitConsent_Minor_FailsWithoutGuardianSignature() {
        // Arrange
        when(securityHelper.getCurrentHospitalId()).thenReturn(1L);

        Patient patient = new Patient();
        patient.setId(10L);
        patient.setHospitalId(1L);
        patient.setAge(15); // Minor
        patient.setGuardianName("Tim Senior");
        patient.setGuardianRelationship("Father");
        when(patientRepository.findByIdAndHospitalIdAndIsActiveTrue(10L, 1L)).thenReturn(Optional.of(patient));

        PatientConsent consent = new PatientConsent();
        consent.setId(15L);
        consent.setHospitalId(1L);
        consent.setPatientId(10L);
        consent.setPatientSigned(true); // Signed by minor (which is invalid, and guardian signature is not present)
        consent.setGuardianSigned(false);
        consent.setConsentType("GENERAL");
        when(patientConsentRepository.findById(15L)).thenReturn(Optional.of(consent));

        // Act & Assert
        assertThatThrownBy(() -> consentService.submitConsent(15L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Guardian signature is mandatory for minors");
    }

    // ===== Surgical Consent (Form 16) =====

    @Test
    void createConsentDraft_Surgery_requiresProcedureName() {
        when(securityHelper.getCurrentHospitalId()).thenReturn(1L);
        Patient patient = new Patient();
        patient.setId(10L);
        patient.setHospitalId(1L);
        patient.setIsActive(true);
        when(patientRepository.findByIdAndHospitalIdAndIsActiveTrue(10L, 1L)).thenReturn(Optional.of(patient));
        IpdAdmission admission = new IpdAdmission();
        admission.setId(20L);
        admission.setHospitalId(1L);
        admission.setPatientId(10L);
        when(ipdAdmissionRepository.findById(20L)).thenReturn(Optional.of(admission));

        ConsentCreateRequest request = new ConsentCreateRequest();
        request.setPatientId(10L);
        request.setAdmissionId(20L);
        request.setConsentType("SURGERY");
        request.setEncounterType("IPD");
        request.setProcedureName(null); // missing

        assertThatThrownBy(() -> consentService.createConsentDraft(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("procedure");
        verify(patientConsentRepository, never()).save(any());
    }

    @Test
    void createConsentDraft_Surgery_initializesSurgicalDetail() {
        when(securityHelper.getCurrentHospitalId()).thenReturn(1L);
        when(securityHelper.getCurrentUserId()).thenReturn(2L);
        Patient patient = new Patient();
        patient.setId(10L);
        patient.setHospitalId(1L);
        patient.setName("John Doe");
        patient.setIsActive(true);
        when(patientRepository.findByIdAndHospitalIdAndIsActiveTrue(10L, 1L)).thenReturn(Optional.of(patient));
        IpdAdmission admission = new IpdAdmission();
        admission.setId(20L);
        admission.setHospitalId(1L);
        admission.setPatientId(10L);
        when(ipdAdmissionRepository.findById(20L)).thenReturn(Optional.of(admission));
        when(patientConsentRepository.findByHospitalIdAndAdmissionIdAndIsDeletedFalse(1L, 20L))
                .thenReturn(new ArrayList<>());
        when(patientConsentRepository.save(any(PatientConsent.class))).thenAnswer(inv -> {
            PatientConsent c = inv.getArgument(0);
            c.setId(77L);
            return c;
        });
        when(surgicalConsentDetailRepository.save(any(SurgicalConsentDetail.class))).thenAnswer(inv -> inv.getArgument(0));

        ConsentCreateRequest request = new ConsentCreateRequest();
        request.setPatientId(10L);
        request.setAdmissionId(20L);
        request.setConsentType("SURGERY");
        request.setEncounterType("IPD");
        request.setProcedureName("Laparoscopic Cholecystectomy");
        request.setSurgeonName("Dr. Mehta");

        PatientConsent result = consentService.createConsentDraft(request);

        assertThat(result.getConsentType()).isEqualTo("SURGERY");
        org.mockito.ArgumentCaptor<SurgicalConsentDetail> captor =
                org.mockito.ArgumentCaptor.forClass(SurgicalConsentDetail.class);
        verify(surgicalConsentDetailRepository).save(captor.capture());
        assertThat(captor.getValue().getConsentId()).isEqualTo(77L);
        assertThat(captor.getValue().getProcedureName()).isEqualTo("Laparoscopic Cholecystectomy");
        assertThat(captor.getValue().getSurgeonName()).isEqualTo("Dr. Mehta");
    }
}
