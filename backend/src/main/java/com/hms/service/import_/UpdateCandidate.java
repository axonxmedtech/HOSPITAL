package com.hms.service.import_;

import java.util.Map;

/**
 * What a row would change on an existing patient — and only the changes. The managed entity was
 * read, compared and left exactly as it was; applying this is the writer's job.
 *
 * @param hospitalId                      the tenant the row was evaluated under; the writer refuses any other
 * @param patientId                       the matched patient (already proven to belong to the caller's hospital)
 * @param expected                        the patient's demographic values AS SEEN at evaluation; the
 *                                        writer reloads and refuses to apply the proposal if any of
 *                                        them changed in between (PATIENT_CHANGED_SINCE_EVALUATION)
 * @param expectedActive                  is_active as seen at evaluation
 * @param expectedAckFor                  duplicate_phone_ack_for as seen at evaluation
 * @param changes                         field key → new value, for fields that differ from the
 *                                        patient's current value and passed the ownership check;
 *                                        dateOfBirth as ISO text. Never contains a blanking.
 * @param reactivate                      the patient is inactive because an UNDONE import
 *                                        deactivated it, and this row brings it back
 * @param clearStalePhoneAcknowledgement  the phone is changing and the patient carries an
 *                                        acknowledgement for the OLD number, which must lapse;
 *                                        detected here, cleared by the writer, never by the evaluator
 * @param legacyId                        MRN for the link when the row carried one
 * @param customFields                    unmapped columns and preserved originals to merge into the link
 * @param hadSnapshot                     whether a last-imported snapshot existed for the ownership check
 */
public record UpdateCandidate(
        Long hospitalId,
        Long patientId,
        PatientFieldValues expected,
        boolean expectedActive,
        String expectedAckFor,
        Map<String, String> changes,
        boolean reactivate,
        boolean clearStalePhoneAcknowledgement,
        String legacyId,
        Map<String, String> customFields,
        boolean hadSnapshot) {
    public UpdateCandidate {
        changes = Map.copyOf(changes);
        customFields = Map.copyOf(customFields);
    }

    public boolean isNoOp() {
        return changes.isEmpty() && !reactivate && customFields.isEmpty();
    }
}
