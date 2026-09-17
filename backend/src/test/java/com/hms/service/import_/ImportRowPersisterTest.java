package com.hms.service.import_;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.hms.dto.DuplicatePatientMatch;
import com.hms.entity.import_.ImportReasonCode;
import com.hms.entity.import_.ImportRowState;
import com.hms.service.hospital.PatientDuplicateFinder;
import jakarta.validation.ConstraintViolationException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.CannotCreateTransactionException;

/** Exception classification at the row boundary, and what never reaches a log line. */
class ImportRowPersisterTest {

    private static final ImportWriteContext CTX = new ImportWriteContext(7L, 42L, LocalDateTime.of(2026, 9, 18, 10, 0));

    private final ImportedPatientWriter writer = mock(ImportedPatientWriter.class);
    private final PatientDuplicateFinder finder = mock(PatientDuplicateFinder.class);
    private final ImportRowPersister persister = new ImportRowPersister(writer, finder);

    private final ListAppender<ILoggingEvent> logs = new ListAppender<>();
    private final Logger logger = (Logger) LoggerFactory.getLogger(ImportRowPersister.class);

    private static final String PII = "SECRET-CUSTOM-VALUE";
    private final CreateCandidate create = new CreateCandidate(
            7L, new PatientFieldValues("Test Person", "9000000001", "MALE", LocalDate.of(1990, 1, 1), null, null, null), "MRN-1", Map.of("Note", PII));
    private final UpdateCandidate update = new UpdateCandidate(
            7L, 10L, new PatientFieldValues("Test Person", "9000000001", "MALE", null, null, null, null), true, null,
            Map.of("phone", "9000000002"), false, true, "MRN-1", Map.of("Note", PII), true);

    @BeforeEach
    void captureLogs() {
        logs.start();
        logger.addAppender(logs);
    }

    @AfterEach
    void noPiiInLogs() {
        logger.detachAppender(logs);
        for (ILoggingEvent e : logs.list) {
            assertThat(e.getFormattedMessage()).doesNotContain(PII).doesNotContain("9000000001").doesNotContain("9000000002").doesNotContain("Test Person");
        }
    }

    private static DataIntegrityViolationException v21() {
        return new DataIntegrityViolationException("could not execute statement",
                new java.sql.SQLIntegrityConstraintViolationException("Duplicate entry '7-9000000002' for key 'patients.uq_patient_active_phone'"));
    }

    private static DataIntegrityViolationException otherConstraint() {
        return new DataIntegrityViolationException("could not execute statement",
                new java.sql.SQLIntegrityConstraintViolationException("Duplicate entry '7-MRN-1' for key 'patient_import_links.uk_patient_import_link_legacy'"));
    }

    @Test
    void aV21RefusalOnCreateIsADuplicatePhoneRaceNamingTheWinner() {
        when(writer.create(any(), any())).thenThrow(v21());
        when(finder.findActiveByPhone(7L, "9000000001", null)).thenReturn(List.of(new DuplicatePatientMatch(30L, "p", "PAT30", "W", 1)));

        ImportWriteResult r = persister.create(create, CTX);

        assertThat(r.state()).isEqualTo(ImportRowState.NEEDS_REVIEW);
        assertThat(r.reasonCode()).isEqualTo(ImportReasonCode.DUPLICATE_PHONE_RACE);
        assertThat(r.relatedPatientIds()).containsExactly(30L);
        assertThat(r.message()).contains("PAT30").doesNotContain("uq_patient").doesNotContain("SQL");
    }

    @Test
    void aV21RefusalOnUpdateExcludesThePatientItselfAndStaysARaceEvenIfTheWinnerVanished() {
        when(writer.update(any(), any())).thenThrow(v21());
        when(finder.findActiveByPhone(7L, "9000000002", 10L)).thenReturn(List.of());

        ImportWriteResult r = persister.update(update, CTX);

        assertThat(r.reasonCode()).isEqualTo(ImportReasonCode.DUPLICATE_PHONE_RACE);
        assertThat(r.patientId()).isEqualTo(10L);
        assertThat(r.relatedPatientIds()).isEmpty();
        verify(finder).findActiveByPhone(7L, "9000000002", 10L);
    }

    @Test
    void aDifferentConstraintIsConstraintFailedNotARace() {
        when(writer.create(any(), any())).thenThrow(otherConstraint());

        ImportWriteResult r = persister.create(create, CTX);

        assertThat(r.state()).isEqualTo(ImportRowState.FAILED);
        assertThat(r.reasonCode()).isEqualTo(ImportReasonCode.CONSTRAINT_FAILED);
        assertThat(r.message()).doesNotContain("uk_patient_import_link_legacy");
        verify(finder, never()).findActiveByPhone(anyLong(), anyString(), any());
    }

    @Test
    void aChangedPatientIsReview() {
        when(writer.update(any(), any())).thenThrow(new PatientChangedSinceEvaluationException(10L, "address"));

        ImportWriteResult r = persister.update(update, CTX);

        assertThat(r.state()).isEqualTo(ImportRowState.NEEDS_REVIEW);
        assertThat(r.reasonCode()).isEqualTo(ImportReasonCode.PATIENT_CHANGED_SINCE_EVALUATION);
        assertThat(r.patientId()).isEqualTo(10L);
    }

    @Test
    void beanValidationIsValidationFailed() {
        when(writer.create(any(), any())).thenThrow(new ConstraintViolationException("bad", Set.of()));

        ImportWriteResult r = persister.create(create, CTX);

        assertThat(r.state()).isEqualTo(ImportRowState.FAILED);
        assertThat(r.reasonCode()).isEqualTo(ImportReasonCode.VALIDATION_FAILED);
    }

    @Test
    void infrastructureFailuresPropagateUntouched() {
        when(writer.create(any(), any())).thenThrow(new CannotCreateTransactionException("no connection"));
        assertThatThrownBy(() -> persister.create(create, CTX)).isInstanceOf(CannotCreateTransactionException.class);

        org.mockito.Mockito.doThrow(new CannotAcquireLockException("deadlock")).when(writer).update(any(), any());
        assertThatThrownBy(() -> persister.update(update, CTX)).isInstanceOf(CannotAcquireLockException.class);

        org.mockito.Mockito.doThrow(new ImportTenantMismatchException(8L, 7L)).when(writer).update(any(), any());
        assertThatThrownBy(() -> persister.update(update, CTX)).isInstanceOf(ImportTenantMismatchException.class);
    }

    @Test
    void nonSuccessEvaluationsPassThroughWithoutTouchingTheWriter() {
        RowEvaluation review = new RowEvaluation(5, ImportRowState.NEEDS_REVIEW, ImportReasonCode.PHONE_MISSING, "Phone", "msg", null, null, List.of(), null, null);

        ImportWriteResult r = persister.persist(review, CTX);

        assertThat(r.state()).isEqualTo(ImportRowState.NEEDS_REVIEW);
        assertThat(r.reasonCode()).isEqualTo(ImportReasonCode.PHONE_MISSING);
        verify(writer, never()).create(any(), any());
        verify(writer, never()).update(any(), any());
    }

    @Test
    void successfulWritesReportTheirIds() {
        when(writer.create(any(), any())).thenReturn(100L);
        when(writer.update(any(), any())).thenReturn(10L);

        assertThat(persister.create(create, CTX)).isEqualTo(ImportWriteResult.created(100L));
        assertThat(persister.update(update, CTX)).isEqualTo(ImportWriteResult.updated(10L));
    }
}
