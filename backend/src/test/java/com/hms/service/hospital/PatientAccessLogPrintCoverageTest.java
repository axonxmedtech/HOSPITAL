package com.hms.service.hospital;

import com.hms.audit.PatientRecordAccess;
import com.hms.audit.PatientRecordAccess.Action;
import com.hms.audit.PatientRecordAccess.Kind;
import com.hms.controller.hospital.IpdAdmissionController;
import com.hms.entity.PatientAccessLog;
import com.hms.repository.PatientAccessLogRepository;
import com.hms.repository.UserRepository;
import com.hms.security.SecurityContextHelper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Every print of a patient's IPD paperwork lands in the access log. The discharge summary and
 * the advance receipt were the two IPD PDFs that did not; the receipt needed a PAYMENT kind,
 * traced to its patient through the bill.
 */
@ExtendWith(MockitoExtension.class)
class PatientAccessLogPrintCoverageTest {

    @Mock PatientAccessLogRepository repository;
    @Mock UserRepository userRepository;
    @Mock SecurityContextHelper securityHelper;
    @InjectMocks PatientAccessLogService service;

    private static PatientRecordAccess annotationOf(String handler) {
        Method m = Arrays.stream(IpdAdmissionController.class.getDeclaredMethods())
                .filter(x -> x.getName().equals(handler)).findFirst().orElseThrow();
        return m.getAnnotation(PatientRecordAccess.class);
    }

    @Test
    void theDischargeSummaryAndTheAdvanceReceiptAreLoggedAsPrints() {
        PatientRecordAccess summary = annotationOf("dischargeSummaryPdf");
        assertThat(summary).as("discharge summary PDF must be access-logged").isNotNull();
        assertThat(summary.kind()).isEqualTo(Kind.ADMISSION);
        assertThat(summary.action()).isEqualTo(Action.PRINT);

        PatientRecordAccess receipt = annotationOf("advanceReceipt");
        assertThat(receipt).as("advance receipt PDF must be access-logged").isNotNull();
        assertThat(receipt.kind()).isEqualTo(Kind.PAYMENT);
        assertThat(receipt.action()).isEqualTo(Action.PRINT);
    }

    @Test
    void aPaymentIdIsTracedToItsPatientWithinTheHospital() {
        when(securityHelper.getCurrentHospitalId()).thenReturn(7L);
        when(repository.patientOfPayment(55L, 7L)).thenReturn(List.of(500L));

        service.logAccess(Kind.PAYMENT, "55", Action.PRINT, "ADVANCE_RECEIPT");

        ArgumentCaptor<PatientAccessLog> row = ArgumentCaptor.forClass(PatientAccessLog.class);
        verify(repository).save(row.capture());
        assertThat(row.getValue().getPatientId()).isEqualTo(500L);
        assertThat(row.getValue().getRecordType()).isEqualTo("ADVANCE_RECEIPT");
        assertThat(row.getValue().getHospitalId()).isEqualTo(7L);
    }

    @Test
    void aPaymentFromAnotherHospitalTracesToNoPatient() {
        when(securityHelper.getCurrentHospitalId()).thenReturn(7L);
        when(repository.patientOfPayment(any(), any())).thenReturn(List.of());

        service.logAccess(Kind.PAYMENT, "55", Action.PRINT, "ADVANCE_RECEIPT");

        ArgumentCaptor<PatientAccessLog> row = ArgumentCaptor.forClass(PatientAccessLog.class);
        verify(repository).save(row.capture());
        assertThat(row.getValue().getPatientId()).isNull();
    }
}
