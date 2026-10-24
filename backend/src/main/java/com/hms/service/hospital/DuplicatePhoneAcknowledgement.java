package com.hms.service.hospital;

import com.hms.entity.Patient;

/**
 * The one place that clears a patient's duplicate-phone acknowledgement.
 *
 * <p>The acknowledgement is value-bound (see {@code Patient.duplicatePhoneAckFor}): it describes
 * one specific number. When the number changes, the acknowledgement no longer describes the
 * patient, and a record claiming staff acknowledged a number the patient does not have is a
 * misleading audit trail — so it is cleared. Strictly redundant for the invariant (every read
 * compares the acknowledgement against the current phone, and V21 does the same), kept for the
 * audit trail's honesty.
 *
 * <p>Extracted from {@code PatientService} so the legacy importer applies exactly the same
 * operation; the manual path's behaviour is unchanged.
 */
public final class DuplicatePhoneAcknowledgement {

    private DuplicatePhoneAcknowledgement() {}

    /** True when the recorded acknowledgement is for a number other than {@code phone} (or there is none for it). */
    public static boolean isStaleFor(Patient patient, String phone) {
        return patient.getDuplicatePhoneAckFor() != null && !patient.getDuplicatePhoneAckFor().equals(phone);
    }

    public static void clear(Patient patient) {
        patient.setDuplicatePhoneAckFor(null);
        patient.setDuplicatePhoneAckAt(null);
        patient.setDuplicatePhoneAckBy(null);
    }
}
