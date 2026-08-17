package com.hms.service.documents;

import com.hms.entity.IpdAdmission;
import com.hms.entity.Patient;
import com.hms.entity.PatientDocument;
import com.hms.repository.IpdAdmissionRepository;
import com.hms.repository.PatientDocumentRepository;
import com.hms.repository.PatientNurseAssignmentRepository;
import com.hms.repository.PatientRepository;
import com.hms.security.SecurityContextHelper;
import com.hms.service.AuditLogService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.io.ByteArrayInputStream;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Who may do what.
 *
 * <p>Reception, doctors and nurses attach records; only an admin removes one, because a deleted
 * lab report is a lost clinical record and the person who uploaded it by mistake is rarely the
 * right person to decide it should vanish.
 */
@ExtendWith(MockitoExtension.class)
class PatientDocumentPermissionsTest {

    @Mock PatientDocumentRepository documentRepository;
    @Mock PatientRepository patientRepository;
    @Mock PatientNurseAssignmentRepository assignmentRepository;
    @Mock IpdAdmissionRepository admissionRepository;
    @Mock DocumentStorage storage;
    @Mock UploadedFileValidator validator;
    @Mock SecurityContextHelper securityHelper;
    @Mock AuditLogService auditLogService;

    @InjectMocks PatientDocumentService service;

    private static final Long HOSPITAL_ID = 7L;
    private static final Long PATIENT_ID = 500L;
    private static final Long ADMISSION_ID = 900L;
    private static final Long NURSE_ID = 20L;

    private Patient patient() {
        Patient p = new Patient();
        p.setId(PATIENT_ID);
        p.setPublicId("pat-1");
        p.setHospitalId(HOSPITAL_ID);
        return p;
    }

    private IpdAdmission admission() {
        IpdAdmission a = new IpdAdmission();
        a.setId(ADMISSION_ID);
        a.setPatientId(PATIENT_ID);
        a.setHospitalId(HOSPITAL_ID);
        return a;
    }

    private PatientDocument document() {
        PatientDocument d = new PatientDocument();
        d.setPublicId("doc-1");
        d.setHospitalId(HOSPITAL_ID);
        d.setPatientId(PATIENT_ID);
        d.setTitle("Blood test");
        d.setStoredFilename("uuid.pdf");
        d.setIsActive(true);
        return d;
    }

    @Test
    void receptionDoctorAndNurseMayUpload() {
        assertThat(PatientDocumentService.canUpload("RECEPTIONIST")).isTrue();
        assertThat(PatientDocumentService.canUpload("DOCTOR")).isTrue();
        assertThat(PatientDocumentService.canUpload("NURSE")).isTrue();
        assertThat(PatientDocumentService.canUpload("NURSE_INCHARGE")).isTrue();
        assertThat(PatientDocumentService.canUpload("HOSPITAL_ADMIN")).isTrue();
    }

    @Test
    void aPharmacistHasNoBusinessAttachingClinicalRecords() {
        assertThat(PatientDocumentService.canUpload("PHARMACIST")).isFalse();
    }

    @Test
    void onlyAnAdminMayDelete() {
        assertThat(PatientDocumentService.canDelete("HOSPITAL_ADMIN")).isTrue();
        assertThat(PatientDocumentService.canDelete("DOCTOR")).isFalse();
        assertThat(PatientDocumentService.canDelete("RECEPTIONIST")).isFalse();
        assertThat(PatientDocumentService.canDelete("NURSE")).isFalse();
    }

    /** A null role is not a role. Defaulting to permitted is how features leak. */
    @Test
    void anUnknownRoleIsRefused() {
        assertThat(PatientDocumentService.canUpload(null)).isFalse();
        assertThat(PatientDocumentService.canDelete(null)).isFalse();
    }

    /** Only nurses carry the assigned-patient restriction; other roles are hospital-wide. */
    @Test
    void onlyNursesAreLimitedToTheirOwnPatients() {
        assertThat(PatientDocumentService.isAssignmentScoped("NURSE")).isTrue();
        assertThat(PatientDocumentService.isAssignmentScoped("NURSE_INCHARGE")).isFalse();
        assertThat(PatientDocumentService.isAssignmentScoped("DOCTOR")).isFalse();
        assertThat(PatientDocumentService.isAssignmentScoped("RECEPTIONIST")).isFalse();
    }

    /**
     * A lab report is at least as private as vitals or notes, both of which already refuse a
     * staff nurse a patient they are not assigned to. Reading the list must carry the same rule
     * as attaching one, or documents would be the one clinical record any nurse could browse.
     */
    @Test
    void listIsRefusedForANurseNotAssignedToThePatient() {
        when(securityHelper.getCurrentHospitalId()).thenReturn(HOSPITAL_ID);
        when(patientRepository.findByPublicIdAndHospitalIdAndIsActiveTrue("pat-1", HOSPITAL_ID))
                .thenReturn(Optional.of(patient()));
        when(securityHelper.getCurrentUserRole()).thenReturn("NURSE");
        when(securityHelper.getCurrentUserId()).thenReturn(NURSE_ID);
        when(admissionRepository.findByPatientIdOrderByAdmissionDatetimeDesc(PATIENT_ID))
                .thenReturn(List.of(admission()));
        when(assignmentRepository.existsByIpdAdmissionIdAndNurseUserIdAndIsActiveTrue(ADMISSION_ID, NURSE_ID))
                .thenReturn(false);

        assertThatThrownBy(() -> service.list("pat-1"))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void downloadIsRefusedForANurseNotAssignedToThePatient() {
        when(securityHelper.getCurrentHospitalId()).thenReturn(HOSPITAL_ID);
        when(documentRepository.findByPublicIdAndHospitalId("doc-1", HOSPITAL_ID))
                .thenReturn(Optional.of(document()));
        when(securityHelper.getCurrentUserRole()).thenReturn("NURSE");
        when(securityHelper.getCurrentUserId()).thenReturn(NURSE_ID);
        when(admissionRepository.findByPatientIdOrderByAdmissionDatetimeDesc(PATIENT_ID))
                .thenReturn(List.of(admission()));
        when(assignmentRepository.existsByIpdAdmissionIdAndNurseUserIdAndIsActiveTrue(ADMISSION_ID, NURSE_ID))
                .thenReturn(false);

        assertThatThrownBy(() -> service.download("doc-1"))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void aNurseAssignedToThePatientCanListAndDownload() {
        when(securityHelper.getCurrentHospitalId()).thenReturn(HOSPITAL_ID);
        when(securityHelper.getCurrentUserRole()).thenReturn("NURSE");
        when(securityHelper.getCurrentUserId()).thenReturn(NURSE_ID);
        when(admissionRepository.findByPatientIdOrderByAdmissionDatetimeDesc(PATIENT_ID))
                .thenReturn(List.of(admission()));
        when(assignmentRepository.existsByIpdAdmissionIdAndNurseUserIdAndIsActiveTrue(ADMISSION_ID, NURSE_ID))
                .thenReturn(true);

        when(patientRepository.findByPublicIdAndHospitalIdAndIsActiveTrue("pat-1", HOSPITAL_ID))
                .thenReturn(Optional.of(patient()));
        when(documentRepository.findByHospitalIdAndPatientIdAndIsActiveTrueOrderByUploadedAtDesc(
                HOSPITAL_ID, PATIENT_ID)).thenReturn(List.of(document()));
        assertThat(service.list("pat-1")).hasSize(1);

        when(documentRepository.findByPublicIdAndHospitalId("doc-1", HOSPITAL_ID))
                .thenReturn(Optional.of(document()));
        when(storage.read(HOSPITAL_ID, "uuid.pdf")).thenReturn(new ByteArrayInputStream("x".getBytes()));
        assertThat(service.download("doc-1")).isNotNull();
    }

    /** A nurse incharge runs the ward, not a caseload, so no assignment lookup ever happens. */
    @Test
    void aNurseInchargeCanListAndDownloadWithoutAnyAssignment() {
        when(securityHelper.getCurrentHospitalId()).thenReturn(HOSPITAL_ID);
        when(securityHelper.getCurrentUserRole()).thenReturn("NURSE_INCHARGE");

        when(patientRepository.findByPublicIdAndHospitalIdAndIsActiveTrue("pat-1", HOSPITAL_ID))
                .thenReturn(Optional.of(patient()));
        when(documentRepository.findByHospitalIdAndPatientIdAndIsActiveTrueOrderByUploadedAtDesc(
                HOSPITAL_ID, PATIENT_ID)).thenReturn(List.of(document()));
        assertThat(service.list("pat-1")).hasSize(1);

        when(documentRepository.findByPublicIdAndHospitalId("doc-1", HOSPITAL_ID))
                .thenReturn(Optional.of(document()));
        when(storage.read(HOSPITAL_ID, "uuid.pdf")).thenReturn(new ByteArrayInputStream("x".getBytes()));
        assertThat(service.download("doc-1")).isNotNull();

        verifyNoInteractions(assignmentRepository, admissionRepository);
    }
}
