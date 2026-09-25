package com.hms.service.import_;

import com.hms.entity.import_.ImportReasonCode;
import com.hms.entity.import_.ImportRowState;
import java.util.List;

/**
 * What persisting one evaluated row actually did. Only these outcomes exist at the write
 * boundary; anything that is not a row-local problem (a lost connection, a broken transaction
 * manager) is not represented here — it propagates, so the batch layer can decide whether the
 * batch itself failed rather than recording "row failed" a hundred thousand times.
 *
 * @param state             CREATED / UPDATED as confirmed by the database, or the downgrade
 * @param reasonCode        for NEEDS_REVIEW / FAILED; null on success
 * @param patientId         the written or matched patient, when known
 * @param relatedPatientIds other patients involved (the holders that won a phone race), own tenant only
 * @param message           administrator-facing text; never SQL, never a stack trace, never cell content
 */
public record ImportWriteResult(
        ImportRowState state, ImportReasonCode reasonCode, Long patientId, List<Long> relatedPatientIds, String message) {

    public ImportWriteResult {
        relatedPatientIds = relatedPatientIds == null ? List.of() : List.copyOf(relatedPatientIds);
    }

    public static ImportWriteResult created(Long patientId) {
        return new ImportWriteResult(ImportRowState.CREATED, null, patientId, List.of(), null);
    }

    public static ImportWriteResult updated(Long patientId) {
        return new ImportWriteResult(ImportRowState.UPDATED, null, patientId, List.of(), null);
    }

    public static ImportWriteResult review(ImportReasonCode code, Long patientId, List<Long> related, String message) {
        return new ImportWriteResult(ImportRowState.NEEDS_REVIEW, code, patientId, related, message);
    }

    public static ImportWriteResult failed(ImportReasonCode code, Long patientId, String message) {
        return new ImportWriteResult(ImportRowState.FAILED, code, patientId, List.of(), message);
    }

    public boolean isSuccess() {
        return state == ImportRowState.CREATED || state == ImportRowState.UPDATED;
    }
}
