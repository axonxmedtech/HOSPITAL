package com.hms.service.import_;

import com.hms.dto.DuplicatePatientMatch;
import com.hms.entity.import_.ImportReasonCode;
import com.hms.entity.import_.ImportRowState;
import com.hms.service.hospital.DuplicatePhoneConstraint;
import com.hms.service.hospital.PatientDuplicateFinder;
import jakarta.validation.ConstraintViolationException;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.TransactionSystemException;

/**
 * The boundary between one row's transaction and the row's outcome. Deliberately NOT
 * transactional: it calls {@link ImportedPatientWriter} cross-bean, so by the time an exception
 * arrives here the row transaction has already rolled back, and classifying it — including the
 * read query that names the winners of a phone race — runs in a fresh session rather than a
 * doomed one. This is the same architecture {@code PatientService} uses for manual registration.
 *
 * <p>Row-local problems become outcomes: the V21 index refusing the phone
 * ({@code DUPLICATE_PHONE_RACE}), a proposal computed from state that has since changed
 * ({@code PATIENT_CHANGED_SINCE_EVALUATION}), bean validation ({@code VALIDATION_FAILED}) and any
 * other deterministic constraint ({@code CONSTRAINT_FAILED}). Everything else — no connection, a
 * broken transaction manager, an unexpected persistence failure — is not a row's fault and is
 * rethrown untouched, so the batch layer can stop instead of recording it a hundred thousand
 * times. A tenant mismatch is a programming error and likewise propagates.
 *
 * <p>Messages returned to the administrator carry a patient number at most; no SQL, no
 * exception text, no cell content. Logs carry ids and masked phones only.
 */
@Component
public class ImportRowPersister {

    private static final Logger log = LoggerFactory.getLogger(ImportRowPersister.class);

    private final ImportedPatientWriter writer;
    private final PatientDuplicateFinder duplicateFinder;

    public ImportRowPersister(ImportedPatientWriter writer, PatientDuplicateFinder duplicateFinder) {
        this.writer = writer;
        this.duplicateFinder = duplicateFinder;
    }

    /**
     * Persists a row that evaluation approved. Rows in any other state are returned as they are —
     * the writer never sees them.
     */
    public ImportWriteResult persist(RowEvaluation evaluation, ImportWriteContext ctx) {
        if (evaluation.state() == ImportRowState.CREATED) {
            return create(evaluation.create(), ctx);
        }
        if (evaluation.state() == ImportRowState.UPDATED) {
            return update(evaluation.update(), ctx);
        }
        return new ImportWriteResult(
                evaluation.state(), evaluation.reasonCode(), evaluation.matchedPatientId(), evaluation.relatedPatientIds(), evaluation.message());
    }

    public ImportWriteResult create(CreateCandidate candidate, ImportWriteContext ctx) {
        try {
            return ImportWriteResult.created(writer.create(candidate, ctx));
        } catch (DataIntegrityViolationException e) {
            return classifyIntegrityViolation(e, ctx, candidate.values().phone(), null);
        } catch (ConstraintViolationException e) {
            return validationFailed(null);
        } catch (TransactionSystemException e) {
            if (rootCause(e) instanceof ConstraintViolationException) return validationFailed(null);
            throw e; // the transaction machinery itself failed: infrastructure
        }
    }

    public ImportWriteResult update(UpdateCandidate candidate, ImportWriteContext ctx) {
        Long id = candidate.patientId();
        try {
            return ImportWriteResult.updated(writer.update(candidate, ctx));
        } catch (PatientChangedSinceEvaluationException e) {
            log.info("Import row for patient {} not applied: {} changed since evaluation", id, e.getWhat());
            return ImportWriteResult.review(
                    ImportReasonCode.PATIENT_CHANGED_SINCE_EVALUATION,
                    id,
                    List.of(),
                    "This patient was changed in the system after the preview was taken, so the row was not applied. "
                            + "Run the preview again to see the current state.");
        } catch (DataIntegrityViolationException e) {
            return classifyIntegrityViolation(e, ctx, candidate.changes().get("phone"), id);
        } catch (ConstraintViolationException e) {
            return validationFailed(id);
        } catch (TransactionSystemException e) {
            if (rootCause(e) instanceof ConstraintViolationException) return validationFailed(id);
            throw e;
        }
    }

    /**
     * Only the active-phone index is a race. Its winners are looked up afterwards, in a new
     * session; if the winner's own transaction rolled back too the index still refused us, so it
     * is still reported as a race (a retry will succeed), just without anyone to name.
     */
    private ImportWriteResult classifyIntegrityViolation(
            DataIntegrityViolationException e, ImportWriteContext ctx, String phone, Long patientId) {
        if (!DuplicatePhoneConstraint.isViolation(e)) {
            log.warn("Import row{} rejected by a database constraint: {}",
                    patientId == null ? "" : " for patient " + patientId, e.getMostSpecificCause().getClass().getSimpleName());
            return ImportWriteResult.failed(
                    ImportReasonCode.CONSTRAINT_FAILED,
                    patientId,
                    "The database refused this row because it conflicts with existing data. Nothing on it was applied.");
        }
        List<DuplicatePatientMatch> holders =
                phone == null ? List.of() : duplicateFinder.findActiveByPhone(ctx.hospitalId(), phone, patientId);
        log.info("Hospital {} import lost a duplicate-phone race on {}", ctx.hospitalId(), PatientDuplicateFinder.maskPhone(phone));
        String holder = holders.size() == 1 && holders.get(0).customId() != null ? " (" + holders.get(0).customId() + ")" : "";
        return ImportWriteResult.review(
                ImportReasonCode.DUPLICATE_PHONE_RACE,
                patientId,
                holders.stream().map(DuplicatePatientMatch::id).toList(),
                "Another patient" + holder + " was registered with this phone number while the import was running. "
                        + "Nothing on this row was applied; review it as a shared number.");
    }

    private static ImportWriteResult validationFailed(Long patientId) {
        return ImportWriteResult.failed(
                ImportReasonCode.VALIDATION_FAILED, patientId, "The row's values do not satisfy the patient record's rules. Nothing on it was applied.");
    }

    private static Throwable rootCause(Throwable t) {
        Throwable c = t;
        while (c.getCause() != null && c.getCause() != c) c = c.getCause();
        return c;
    }
}
