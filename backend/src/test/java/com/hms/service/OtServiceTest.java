package com.hms.service;

import com.hms.dto.OtBookingRequest;
import com.hms.dto.OtChecklistRequest;
import com.hms.entity.*;
import com.hms.exception.UnauthorizedException;
import com.hms.repository.*;
import com.hms.security.SecurityContextHelper;
import com.hms.security.HospitalWebSocketHandler;
import com.hms.service.hospital.OtService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OtServiceTest {

    @Mock
    private OtBookingRepository bookingRepository;

    @Mock
    private OtChecklistRepository checklistRepository;

    @Mock
    private IpdAdmissionRepository ipdAdmissionRepository;

    @Mock
    private SecurityContextHelper securityHelper;

    @Mock
    private AuditLogRepository auditLogRepository;

    @Mock
    private HospitalWebSocketHandler webSocketHandler;

    @Mock
    private com.hms.service.hospital.MrdService mrdService;

    @Mock
    private com.hms.service.hospital.BillingService billingService;

    @Mock
    private OperationRecordRepository operationRecordRepository;

    @Mock
    private AnaesthesiaRecordRepository anaesthesiaRecordRepository;

    @InjectMocks
    private OtService otService;

    // ===== Operation Record (Form 18) =====

    private OtBooking tenantBooking(Long bookingId, Long hospitalId, Long admissionId) {
        OtBooking booking = new OtBooking();
        booking.setId(bookingId);
        booking.setHospitalId(hospitalId);
        booking.setIpdAdmissionId(admissionId);
        booking.setSurgeonId(7L);
        booking.setProcedureName("Appendectomy");
        return booking;
    }

    @Test
    void createOperationRecord_rejectedBeforeTimeOut() {
        Long hospitalId = 1L, bookingId = 5L;
        when(securityHelper.getCurrentHospitalId()).thenReturn(hospitalId);
        when(bookingRepository.findById(bookingId)).thenReturn(Optional.of(tenantBooking(bookingId, hospitalId, 10L)));
        OtChecklist checklist = new OtChecklist();
        checklist.setSignInCompleted(true);
        checklist.setTimeOutCompleted(false);
        when(checklistRepository.findByOtBookingIdAndHospitalId(bookingId, hospitalId)).thenReturn(Optional.of(checklist));

        assertThatThrownBy(() -> otService.createOperationRecord(bookingId, new com.hms.dto.OperationRecordRequest()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("time-out");
        verify(operationRecordRepository, never()).save(any());
    }

    @Test
    void createOperationRecord_succeedsAfterTimeOut() {
        Long hospitalId = 1L, bookingId = 5L;
        when(securityHelper.getCurrentHospitalId()).thenReturn(hospitalId);
        when(bookingRepository.findById(bookingId)).thenReturn(Optional.of(tenantBooking(bookingId, hospitalId, 10L)));
        OtChecklist checklist = new OtChecklist();
        checklist.setSignInCompleted(true);
        checklist.setTimeOutCompleted(true);
        when(checklistRepository.findByOtBookingIdAndHospitalId(bookingId, hospitalId)).thenReturn(Optional.of(checklist));
        when(operationRecordRepository.findByOtBookingIdAndHospitalId(bookingId, hospitalId)).thenReturn(Optional.empty());
        when(operationRecordRepository.save(any(OperationRecord.class))).thenAnswer(inv -> inv.getArgument(0));

        com.hms.dto.OperationRecordRequest req = new com.hms.dto.OperationRecordRequest();
        req.setActualProcedure("Laparoscopic appendectomy");
        OperationRecord saved = otService.createOperationRecord(bookingId, req);

        assertThat(saved.getStatus()).isEqualTo("DRAFT");
        assertThat(saved.getOtBookingId()).isEqualTo(bookingId);
        assertThat(saved.getProcedureName()).isEqualTo("Appendectomy"); // fell back to booking
        assertThat(saved.getSurgeonId()).isEqualTo(7L);
    }

    @Test
    void createOperationRecord_rejectsCrossTenant() {
        Long hospitalId = 1L, bookingId = 5L;
        when(securityHelper.getCurrentHospitalId()).thenReturn(hospitalId);
        when(bookingRepository.findById(bookingId)).thenReturn(Optional.of(tenantBooking(bookingId, 99L, 10L)));

        assertThatThrownBy(() -> otService.createOperationRecord(bookingId, new com.hms.dto.OperationRecordRequest()))
                .isInstanceOf(UnauthorizedException.class);
        verify(operationRecordRepository, never()).save(any());
    }

    @Test
    void finalizeOperationRecord_rejectedWithoutProcedureOrPlan() {
        Long hospitalId = 1L, bookingId = 5L;
        when(securityHelper.getCurrentHospitalId()).thenReturn(hospitalId);
        when(bookingRepository.findById(bookingId)).thenReturn(Optional.of(tenantBooking(bookingId, hospitalId, 10L)));
        OperationRecord record = new OperationRecord();
        record.setStatus("DRAFT");
        record.setActualProcedure(null); // missing
        when(operationRecordRepository.findByOtBookingIdAndHospitalId(bookingId, hospitalId)).thenReturn(Optional.of(record));

        assertThatThrownBy(() -> otService.finalizeOperationRecord(bookingId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("actual procedure");
        verify(operationRecordRepository, never()).save(any());
    }

    @Test
    void finalizeOperationRecord_rejectedBeforeSignOut() {
        Long hospitalId = 1L, bookingId = 5L;
        when(securityHelper.getCurrentHospitalId()).thenReturn(hospitalId);
        when(securityHelper.getCurrentUserEmail()).thenReturn("surgeon@hospital.com");
        when(bookingRepository.findById(bookingId)).thenReturn(Optional.of(tenantBooking(bookingId, hospitalId, 10L)));
        OperationRecord record = new OperationRecord();
        record.setStatus("DRAFT");
        record.setActualProcedure("Lap appendectomy");
        record.setPostOpPlan("ICU overnight, IV antibiotics");
        when(operationRecordRepository.findByOtBookingIdAndHospitalId(bookingId, hospitalId)).thenReturn(Optional.of(record));
        OtChecklist checklist = new OtChecklist();
        checklist.setSignOutCompleted(false);
        when(checklistRepository.findByOtBookingIdAndHospitalId(bookingId, hospitalId)).thenReturn(Optional.of(checklist));

        assertThatThrownBy(() -> otService.finalizeOperationRecord(bookingId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("sign-out");
        verify(operationRecordRepository, never()).save(any());
    }

    @Test
    void finalizeOperationRecord_stampsSignatureWhenReady() {
        Long hospitalId = 1L, bookingId = 5L;
        when(securityHelper.getCurrentHospitalId()).thenReturn(hospitalId);
        when(securityHelper.getCurrentUserEmail()).thenReturn("surgeon@hospital.com");
        when(bookingRepository.findById(bookingId)).thenReturn(Optional.of(tenantBooking(bookingId, hospitalId, 10L)));
        OperationRecord record = new OperationRecord();
        record.setStatus("DRAFT");
        record.setActualProcedure("Lap appendectomy");
        record.setPostOpPlan("ICU overnight, IV antibiotics");
        when(operationRecordRepository.findByOtBookingIdAndHospitalId(bookingId, hospitalId)).thenReturn(Optional.of(record));
        OtChecklist checklist = new OtChecklist();
        checklist.setSignOutCompleted(true);
        when(checklistRepository.findByOtBookingIdAndHospitalId(bookingId, hospitalId)).thenReturn(Optional.of(checklist));
        when(operationRecordRepository.save(any(OperationRecord.class))).thenAnswer(inv -> inv.getArgument(0));

        OperationRecord finalized = otService.finalizeOperationRecord(bookingId);

        assertThat(finalized.getStatus()).isEqualTo("FINALIZED");
        assertThat(finalized.getSignedBy()).isEqualTo("surgeon@hospital.com");
        assertThat(finalized.getSignedAt()).isNotNull();
    }

    // ===== Anaesthesia Record (Form 19) =====

    @Test
    void startAnaesthesiaRecord_rejectedBeforeSignIn() {
        Long hospitalId = 1L, bookingId = 5L;
        when(securityHelper.getCurrentHospitalId()).thenReturn(hospitalId);
        when(bookingRepository.findById(bookingId)).thenReturn(Optional.of(tenantBooking(bookingId, hospitalId, 10L)));
        OtChecklist checklist = new OtChecklist();
        checklist.setSignInCompleted(false);
        when(checklistRepository.findByOtBookingIdAndHospitalId(bookingId, hospitalId)).thenReturn(Optional.of(checklist));

        assertThatThrownBy(() -> otService.startAnaesthesiaRecord(bookingId, new com.hms.dto.AnaesthesiaRecordRequest()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("sign-in");
        verify(anaesthesiaRecordRepository, never()).save(any());
    }

    @Test
    void startAnaesthesiaRecord_succeedsAfterSignIn() {
        Long hospitalId = 1L, bookingId = 5L;
        when(securityHelper.getCurrentHospitalId()).thenReturn(hospitalId);
        when(bookingRepository.findById(bookingId)).thenReturn(Optional.of(tenantBooking(bookingId, hospitalId, 10L)));
        OtChecklist checklist = new OtChecklist();
        checklist.setSignInCompleted(true);
        when(checklistRepository.findByOtBookingIdAndHospitalId(bookingId, hospitalId)).thenReturn(Optional.of(checklist));
        when(anaesthesiaRecordRepository.findByOtBookingIdAndHospitalId(bookingId, hospitalId)).thenReturn(Optional.empty());
        when(anaesthesiaRecordRepository.save(any(AnaesthesiaRecord.class))).thenAnswer(inv -> inv.getArgument(0));

        com.hms.dto.AnaesthesiaRecordRequest req = new com.hms.dto.AnaesthesiaRecordRequest();
        req.setAnaesthesiaType("GENERAL");
        AnaesthesiaRecord saved = otService.startAnaesthesiaRecord(bookingId, req);

        assertThat(saved.getStatus()).isEqualTo("ACTIVE");
        assertThat(saved.getOtBookingId()).isEqualTo(bookingId);
        assertThat(saved.getAnaesthesiaType()).isEqualTo("GENERAL");
    }

    @Test
    void startAnaesthesiaRecord_rejectsCrossTenant() {
        Long hospitalId = 1L, bookingId = 5L;
        when(securityHelper.getCurrentHospitalId()).thenReturn(hospitalId);
        when(bookingRepository.findById(bookingId)).thenReturn(Optional.of(tenantBooking(bookingId, 99L, 10L)));

        assertThatThrownBy(() -> otService.startAnaesthesiaRecord(bookingId, new com.hms.dto.AnaesthesiaRecordRequest()))
                .isInstanceOf(UnauthorizedException.class);
        verify(anaesthesiaRecordRepository, never()).save(any());
    }

    @Test
    void completeAnaesthesiaRecord_rejectedWithoutType() {
        Long hospitalId = 1L, bookingId = 5L;
        when(securityHelper.getCurrentHospitalId()).thenReturn(hospitalId);
        when(securityHelper.getCurrentUserEmail()).thenReturn("anaes@hospital.com");
        when(bookingRepository.findById(bookingId)).thenReturn(Optional.of(tenantBooking(bookingId, hospitalId, 10L)));
        AnaesthesiaRecord record = new AnaesthesiaRecord();
        record.setStatus("ACTIVE");
        record.setAnaesthesiaType(null);
        when(anaesthesiaRecordRepository.findByOtBookingIdAndHospitalId(bookingId, hospitalId)).thenReturn(Optional.of(record));

        assertThatThrownBy(() -> otService.completeAnaesthesiaRecord(bookingId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("anaesthesia type");
        verify(anaesthesiaRecordRepository, never()).save(any());
    }

    @Test
    void completeAnaesthesiaRecord_stampsSignatureWhenReady() {
        Long hospitalId = 1L, bookingId = 5L;
        when(securityHelper.getCurrentHospitalId()).thenReturn(hospitalId);
        when(securityHelper.getCurrentUserEmail()).thenReturn("anaes@hospital.com");
        when(bookingRepository.findById(bookingId)).thenReturn(Optional.of(tenantBooking(bookingId, hospitalId, 10L)));
        AnaesthesiaRecord record = new AnaesthesiaRecord();
        record.setStatus("ACTIVE");
        record.setAnaesthesiaType("SPINAL");
        when(anaesthesiaRecordRepository.findByOtBookingIdAndHospitalId(bookingId, hospitalId)).thenReturn(Optional.of(record));
        when(anaesthesiaRecordRepository.save(any(AnaesthesiaRecord.class))).thenAnswer(inv -> inv.getArgument(0));

        AnaesthesiaRecord completed = otService.completeAnaesthesiaRecord(bookingId);

        assertThat(completed.getStatus()).isEqualTo("COMPLETED");
        assertThat(completed.getSignedBy()).isEqualTo("anaes@hospital.com");
        assertThat(completed.getSignedAt()).isNotNull();
        assertThat(completed.getCompletionTime()).isNotNull();
    }

    @Test
    void scheduleBooking_savesBookingAndChecklist() {
        Long hospitalId = 1L;
        Long admissionId = 10L;

        // Mock security auditing context
        Authentication authentication = mock(Authentication.class);
        when(authentication.getName()).thenReturn("doctor@hospital.com");
        SecurityContext securityContext = mock(SecurityContext.class);
        when(securityContext.getAuthentication()).thenReturn(authentication);
        SecurityContextHolder.setContext(securityContext);

        when(securityHelper.getCurrentHospitalId()).thenReturn(hospitalId);

        IpdAdmission admission = new IpdAdmission();
        admission.setId(admissionId);
        admission.setHospitalId(hospitalId);
        when(ipdAdmissionRepository.findById(admissionId)).thenReturn(Optional.of(admission));

        // Mock room list to avoid room conflicts
        when(bookingRepository.findByHospitalIdOrderByScheduledDateTimeDesc(hospitalId))
                .thenReturn(new ArrayList<>());

        when(bookingRepository.save(any(OtBooking.class))).thenAnswer(invocation -> {
            OtBooking booking = invocation.getArgument(0);
            booking.setId(100L); // set mock ID
            return booking;
        });

        OtBookingRequest req = new OtBookingRequest(
                "Cholecystectomy", LocalDateTime.now().plusDays(2), 5L, "Dr. Ana", "OT 1", "Scheduled surgery"
        );

        OtBooking booking = otService.scheduleBooking(admissionId, req);

        assertThat(booking.getProcedureName()).isEqualTo("Cholecystectomy");
        assertThat(booking.getOtRoomNumber()).isEqualTo("OT 1");
        assertThat(booking.getStatus()).isEqualTo("SCHEDULED");

        verify(bookingRepository, times(1)).save(any(OtBooking.class));
        verify(checklistRepository, times(1)).save(any(OtChecklist.class));
    }

    @Test
    void scheduleBooking_throwsOnRoomConflict() {
        Long hospitalId = 1L;
        Long admissionId = 10L;
        LocalDateTime testTime = LocalDateTime.now().plusDays(2);

        when(securityHelper.getCurrentHospitalId()).thenReturn(hospitalId);

        IpdAdmission admission = new IpdAdmission();
        admission.setId(admissionId);
        admission.setHospitalId(hospitalId);
        when(ipdAdmissionRepository.findById(admissionId)).thenReturn(Optional.of(admission));

        // Mock an existing conflicting booking scheduled at same time in room 'OT 1'
        OtBooking conflictBooking = new OtBooking();
        conflictBooking.setOtRoomNumber("OT 1");
        conflictBooking.setScheduledDateTime(testTime);
        conflictBooking.setStatus("SCHEDULED");
        when(bookingRepository.findByHospitalIdOrderByScheduledDateTimeDesc(hospitalId))
                .thenReturn(java.util.List.of(conflictBooking));

        OtBookingRequest req = new OtBookingRequest(
                "Cholecystectomy", testTime, 5L, "Dr. Ana", "OT 1", "Conflicting surgery"
        );

        assertThatThrownBy(() -> otService.scheduleBooking(admissionId, req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("is already booked within 1 hour of this time");
    }

    @Test
    void signChecklist_advancesStatusesCorrectly() {
        Long hospitalId = 1L;
        Long bookingId = 100L;

        // Mock security auditing context
        Authentication authentication = mock(Authentication.class);
        when(authentication.getName()).thenReturn("nurse@hospital.com");
        SecurityContext securityContext = mock(SecurityContext.class);
        when(securityContext.getAuthentication()).thenReturn(authentication);
        SecurityContextHolder.setContext(securityContext);

        when(securityHelper.getCurrentHospitalId()).thenReturn(hospitalId);
        when(securityHelper.getCurrentUserEmail()).thenReturn("nurse@hospital.com");

        OtBooking booking = new OtBooking();
        booking.setId(bookingId);
        booking.setHospitalId(hospitalId);
        booking.setStatus("SCHEDULED");
        when(bookingRepository.findById(bookingId)).thenReturn(Optional.of(booking));

        OtChecklist checklist = new OtChecklist();
        checklist.setOtBookingId(bookingId);
        checklist.setHospitalId(hospitalId);
        checklist.setSignInCompleted(true); // pre-requisite for timeout
        when(checklistRepository.findByOtBookingIdAndHospitalId(bookingId, hospitalId))
                .thenReturn(Optional.of(checklist));

        when(checklistRepository.save(any(OtChecklist.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Sign Time Out
        OtChecklistRequest req = new OtChecklistRequest("TIME_OUT", "All prep check done");
        OtChecklist updated = otService.signChecklist(bookingId, req);

        assertThat(updated.isTimeOutCompleted()).isTrue();
        assertThat(updated.getTimeOutBy()).isEqualTo("nurse@hospital.com");
        assertThat(booking.getStatus()).isEqualTo("IN_PROGRESS");
    }
}
