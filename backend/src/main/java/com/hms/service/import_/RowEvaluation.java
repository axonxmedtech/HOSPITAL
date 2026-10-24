package com.hms.service.import_;

import com.hms.entity.import_.ImportReasonCode;
import com.hms.entity.import_.ImportRowState;
import java.util.List;

/**
 * The decision for one row. Exactly one of {@code create} / {@code update} is present when the
 * state is CREATED or UPDATED respectively; neither is present otherwise, so a later phase cannot
 * accidentally persist a row that was refused. {@code state} names the outcome the row WOULD have
 * (the writer confirms or downgrades it — a race at insert time turns CREATED into NEEDS_REVIEW).
 *
 * @param rowNum           1-based spreadsheet row
 * @param state            the decision
 * @param reasonCode       stable reason for any non-success state; null for CREATED/UPDATED
 * @param column           display header the reason is about, when there is one
 * @param message          administrator-facing text; never contains SQL, stack text or another tenant's data
 * @param phoneMasked      the canonical phone, masked, when the reason is about a phone
 * @param matchedPatientId the existing patient the row was compared against, when any
 * @param relatedPatientIds other patients the row collides with (duplicate-phone holders), own tenant only
 */
public record RowEvaluation(
        int rowNum,
        ImportRowState state,
        ImportReasonCode reasonCode,
        String column,
        String message,
        String phoneMasked,
        Long matchedPatientId,
        List<Long> relatedPatientIds,
        CreateCandidate create,
        UpdateCandidate update) {

    public RowEvaluation {
        relatedPatientIds = relatedPatientIds == null ? List.of() : List.copyOf(relatedPatientIds);
        if (state == ImportRowState.CREATED && (create == null || update != null)) {
            throw new IllegalArgumentException("CREATED must carry exactly a create candidate");
        }
        if (state == ImportRowState.UPDATED && (update == null || create != null)) {
            throw new IllegalArgumentException("UPDATED must carry exactly an update candidate");
        }
        if (state != ImportRowState.CREATED && state != ImportRowState.UPDATED && (create != null || update != null)) {
            throw new IllegalArgumentException(state + " must not carry a candidate");
        }
        if ((state == ImportRowState.CREATED || state == ImportRowState.UPDATED) != (reasonCode == null)) {
            throw new IllegalArgumentException("success states carry no reason; every other state carries one");
        }
    }

    static RowEvaluation created(int rowNum, CreateCandidate c) {
        return new RowEvaluation(rowNum, ImportRowState.CREATED, null, null, null, null, null, List.of(), c, null);
    }

    static RowEvaluation updated(int rowNum, UpdateCandidate u) {
        return new RowEvaluation(rowNum, ImportRowState.UPDATED, null, null, null, null, u.patientId(), List.of(), null, u);
    }

    static RowEvaluation skipped(int rowNum, ImportReasonCode code, String message, Long matched) {
        return new RowEvaluation(rowNum, ImportRowState.SKIPPED, code, null, message, null, matched, List.of(), null, null);
    }

    static RowEvaluation failed(int rowNum, ImportReasonCode code, String column, String message) {
        return new RowEvaluation(rowNum, ImportRowState.FAILED, code, column, message, null, null, List.of(), null, null);
    }

    static RowEvaluation review(
            int rowNum, ImportReasonCode code, String column, String message, String phoneMasked, Long matched, List<Long> related) {
        return new RowEvaluation(rowNum, ImportRowState.NEEDS_REVIEW, code, column, message, phoneMasked, matched, related, null, null);
    }

    public boolean isSuccess() {
        return state == ImportRowState.CREATED || state == ImportRowState.UPDATED;
    }
}
