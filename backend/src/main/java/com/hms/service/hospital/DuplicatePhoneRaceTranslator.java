package com.hms.service.hospital;

import com.hms.dto.DuplicatePatientMatch;
import com.hms.exception.DuplicatePhoneConflictException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Turns a lost duplicate-phone race back into the answer Phase A already gives (S-PID-D).
 *
 * <p>The application check and the database constraint enforce the same rule at different moments:
 * the check catches the duplicate that already existed, the constraint catches the duplicate that
 * appeared while this request was deciding. Reception should not be able to tell which one happened
 * — both produce the same chooser listing the patients already on that number.
 *
 * <p><b>Call this only where no transaction is live.</b> The re-read must happen after the failed
 * transaction has rolled back and closed, or it runs inside a doomed one and the whole request dies
 * with UnexpectedRollbackException instead. That is why the appointment path translates at the
 * controller and not inside {@code createAppointment}, which is itself {@code @Transactional}.
 */
@Component
public class DuplicatePhoneRaceTranslator {

    private static final Logger logger = LoggerFactory.getLogger(DuplicatePhoneRaceTranslator.class);

    @Autowired
    private PatientDuplicateFinder patientDuplicateFinder;

    /**
     * @return the structured duplicate-phone conflict when the active-phone index rejected the
     *         write, otherwise the original exception unchanged. An unrelated constraint failure is
     *         never dressed up as a duplicate phone: that would hide a real defect behind a
     *         friendly message.
     */
    public RuntimeException translate(DataIntegrityViolationException exception, Long hospitalId,
            String phone, Long excludePatientId, String duplicateMessage) {
        if (!DuplicatePhoneConstraint.isViolation(exception)) {
            return exception;
        }
        List<DuplicatePatientMatch> conflicts =
                patientDuplicateFinder.findActiveByPhone(hospitalId, phone, excludePatientId);
        if (conflicts.isEmpty()) {
            // The index refused us, but the winner is no longer visible — its own transaction rolled
            // back too. Reporting a conflict against nobody would be a lie; report what happened.
            return exception;
        }
        logger.info("Hospital {} lost a duplicate-phone race on {}; reporting the existing patient(s)",
                hospitalId, PatientDuplicateFinder.maskPhone(phone));
        return new DuplicatePhoneConflictException(duplicateMessage, conflicts);
    }
}
