package com.hms.service.documents;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Who may do what.
 *
 * <p>Reception, doctors and nurses attach records; only an admin removes one, because a deleted
 * lab report is a lost clinical record and the person who uploaded it by mistake is rarely the
 * right person to decide it should vanish.
 */
class PatientDocumentPermissionsTest {

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
}
