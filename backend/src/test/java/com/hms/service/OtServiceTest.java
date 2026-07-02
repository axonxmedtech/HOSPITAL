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

    @Mock
    private PacuRecordRepository pacuRecordRepository;

    @Mock
    private ClinicalHandoverRepository clinicalHandoverRepository;

    @Mock
    private PostopOrdersRepository postopOrdersRepository;

    @Mock
    private OtInstrumentCountRepository instrumentCountRepository;

    @Mock
    private PatientImplantRepository implantRepository;

    @Mock
    private OtReadinessRepository readinessRepository;

    @Mock
    private PatientRepository patientRepository;

    @Mock
    private DoctorRepository doctorRepository;

    @Mock
    private PreAnaesthesiaAssessmentRepository pacRepository;

    @Mock
    private PatientConsentRepository patientConsentRepository;

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

    // ===== PACU / Recovery Record (Form 20) =====

    private com.hms.dto.PacuRecordRequest aldreteRequest(int each) {
        com.hms.dto.PacuRecordRequest req = new com.hms.dto.PacuRecordRequest();
        req.setAldreteActivity(each);
        req.setAldreteRespiration(each);
        req.setAldreteCirculation(each);
        req.setAldreteConsciousness(each);
        req.setAldreteOxygen(each);
        return req;
    }

    @Test
    void startPacuRecord_rejectedBeforeAnaesthesiaCompleted() {
        Long hospitalId = 1L, bookingId = 5L;
        when(securityHelper.getCurrentHospitalId()).thenReturn(hospitalId);
        when(bookingRepository.findById(bookingId)).thenReturn(Optional.of(tenantBooking(bookingId, hospitalId, 10L)));
        AnaesthesiaRecord anaes = new AnaesthesiaRecord();
        anaes.setStatus("ACTIVE"); // not COMPLETED
        when(anaesthesiaRecordRepository.findByOtBookingIdAndHospitalId(bookingId, hospitalId)).thenReturn(Optional.of(anaes));

        assertThatThrownBy(() -> otService.startPacuRecord(bookingId, new com.hms.dto.PacuRecordRequest()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("anaesthesia record is completed");
        verify(pacuRecordRepository, never()).save(any());
    }

    @Test
    void startPacuRecord_computesAldreteAndReadyStatus() {
        Long hospitalId = 1L, bookingId = 5L;
        when(securityHelper.getCurrentHospitalId()).thenReturn(hospitalId);
        when(bookingRepository.findById(bookingId)).thenReturn(Optional.of(tenantBooking(bookingId, hospitalId, 10L)));
        AnaesthesiaRecord anaes = new AnaesthesiaRecord();
        anaes.setStatus("COMPLETED");
        when(anaesthesiaRecordRepository.findByOtBookingIdAndHospitalId(bookingId, hospitalId)).thenReturn(Optional.of(anaes));
        when(pacuRecordRepository.findByOtBookingIdAndHospitalId(bookingId, hospitalId)).thenReturn(Optional.empty());
        when(pacuRecordRepository.save(any(PacuRecord.class))).thenAnswer(inv -> inv.getArgument(0));

        PacuRecord saved = otService.startPacuRecord(bookingId, aldreteRequest(2)); // 5 x 2 = 10

        assertThat(saved.getAldreteScore()).isEqualTo(10);
        assertThat(saved.getStatus()).isEqualTo("READY");
        assertThat(saved.getRecoveryStart()).isNotNull();
    }

    @Test
    void startPacuRecord_lowAldreteStaysActive() {
        Long hospitalId = 1L, bookingId = 5L;
        when(securityHelper.getCurrentHospitalId()).thenReturn(hospitalId);
        when(bookingRepository.findById(bookingId)).thenReturn(Optional.of(tenantBooking(bookingId, hospitalId, 10L)));
        AnaesthesiaRecord anaes = new AnaesthesiaRecord();
        anaes.setStatus("COMPLETED");
        when(anaesthesiaRecordRepository.findByOtBookingIdAndHospitalId(bookingId, hospitalId)).thenReturn(Optional.of(anaes));
        when(pacuRecordRepository.findByOtBookingIdAndHospitalId(bookingId, hospitalId)).thenReturn(Optional.empty());
        when(pacuRecordRepository.save(any(PacuRecord.class))).thenAnswer(inv -> inv.getArgument(0));

        PacuRecord saved = otService.startPacuRecord(bookingId, aldreteRequest(1)); // 5 x 1 = 5

        assertThat(saved.getAldreteScore()).isEqualTo(5);
        assertThat(saved.getStatus()).isEqualTo("ACTIVE");
    }

    @Test
    void transferPacuRecord_rejectedWhenAldreteBelowMin() {
        Long hospitalId = 1L, bookingId = 5L;
        when(securityHelper.getCurrentHospitalId()).thenReturn(hospitalId);
        when(securityHelper.getCurrentUserEmail()).thenReturn("anaes@hospital.com");
        when(bookingRepository.findById(bookingId)).thenReturn(Optional.of(tenantBooking(bookingId, hospitalId, 10L)));
        PacuRecord record = new PacuRecord();
        record.setStatus("ACTIVE");
        record.setAldreteScore(7);
        record.setTransferDestination("WARD");
        record.setHandoverNotes("Stable");
        when(pacuRecordRepository.findByOtBookingIdAndHospitalId(bookingId, hospitalId)).thenReturn(Optional.of(record));

        assertThatThrownBy(() -> otService.transferPacuRecord(bookingId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Aldrete");
        verify(pacuRecordRepository, never()).save(any());
    }

    @Test
    void transferPacuRecord_rejectedWithoutHandover() {
        Long hospitalId = 1L, bookingId = 5L;
        when(securityHelper.getCurrentHospitalId()).thenReturn(hospitalId);
        when(securityHelper.getCurrentUserEmail()).thenReturn("anaes@hospital.com");
        when(bookingRepository.findById(bookingId)).thenReturn(Optional.of(tenantBooking(bookingId, hospitalId, 10L)));
        PacuRecord record = new PacuRecord();
        record.setStatus("READY");
        record.setAldreteScore(10);
        record.setTransferDestination("WARD");
        record.setHandoverNotes(null); // missing
        when(pacuRecordRepository.findByOtBookingIdAndHospitalId(bookingId, hospitalId)).thenReturn(Optional.of(record));

        assertThatThrownBy(() -> otService.transferPacuRecord(bookingId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("handover");
        verify(pacuRecordRepository, never()).save(any());
    }

    @Test
    void transferPacuRecord_succeedsWhenReady() {
        Long hospitalId = 1L, bookingId = 5L;
        when(securityHelper.getCurrentHospitalId()).thenReturn(hospitalId);
        when(securityHelper.getCurrentUserEmail()).thenReturn("anaes@hospital.com");
        when(bookingRepository.findById(bookingId)).thenReturn(Optional.of(tenantBooking(bookingId, hospitalId, 10L)));
        PacuRecord record = new PacuRecord();
        record.setStatus("READY");
        record.setAldreteScore(9);
        record.setTransferDestination("WARD");
        record.setHandoverNotes("Stable, IV antibiotics, monitor pain");
        when(pacuRecordRepository.findByOtBookingIdAndHospitalId(bookingId, hospitalId)).thenReturn(Optional.of(record));
        when(pacuRecordRepository.save(any(PacuRecord.class))).thenAnswer(inv -> inv.getArgument(0));

        PacuRecord transferred = otService.transferPacuRecord(bookingId);

        assertThat(transferred.getStatus()).isEqualTo("TRANSFERRED");
        assertThat(transferred.getSignedBy()).isEqualTo("anaes@hospital.com");
        assertThat(transferred.getSignedAt()).isNotNull();
        assertThat(transferred.getRecoveryEnd()).isNotNull();
    }

    @Test
    void startPacuRecord_rejectsCrossTenant() {
        Long hospitalId = 1L, bookingId = 5L;
        when(securityHelper.getCurrentHospitalId()).thenReturn(hospitalId);
        when(bookingRepository.findById(bookingId)).thenReturn(Optional.of(tenantBooking(bookingId, 99L, 10L)));

        assertThatThrownBy(() -> otService.startPacuRecord(bookingId, new com.hms.dto.PacuRecordRequest()))
                .isInstanceOf(UnauthorizedException.class);
        verify(pacuRecordRepository, never()).save(any());
    }

    // ===== Clinical Handover (Form 22) =====

    private PacuRecord readyPacu(int score) {
        PacuRecord p = new PacuRecord();
        p.setStatus(score >= 9 ? "READY" : "ACTIVE");
        p.setAldreteScore(score);
        return p;
    }

    private com.hms.dto.ClinicalHandoverRequest handoverRequest() {
        com.hms.dto.ClinicalHandoverRequest req = new com.hms.dto.ClinicalHandoverRequest();
        req.setTransportStaff("Nurse Priya");
        req.setDevices("Foley catheter (patent); IV cannula R hand");
        req.setToDepartment("Surgical Ward A");
        return req;
    }

    @Test
    void initiateHandover_rejectedWhenPacuNotReady() {
        Long hospitalId = 1L, bookingId = 5L;
        when(securityHelper.getCurrentHospitalId()).thenReturn(hospitalId);
        when(bookingRepository.findById(bookingId)).thenReturn(Optional.of(tenantBooking(bookingId, hospitalId, 10L)));
        when(pacuRecordRepository.findByOtBookingIdAndHospitalId(bookingId, hospitalId)).thenReturn(Optional.of(readyPacu(6)));

        assertThatThrownBy(() -> otService.initiateHandover(bookingId, handoverRequest()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("recovery-ready");
        verify(clinicalHandoverRepository, never()).save(any());
    }

    @Test
    void initiateHandover_rejectedWithoutDevices() {
        Long hospitalId = 1L, bookingId = 5L;
        when(securityHelper.getCurrentHospitalId()).thenReturn(hospitalId);
        when(bookingRepository.findById(bookingId)).thenReturn(Optional.of(tenantBooking(bookingId, hospitalId, 10L)));
        when(pacuRecordRepository.findByOtBookingIdAndHospitalId(bookingId, hospitalId)).thenReturn(Optional.of(readyPacu(10)));
        com.hms.dto.ClinicalHandoverRequest req = handoverRequest();
        req.setDevices(null);

        assertThatThrownBy(() -> otService.initiateHandover(bookingId, req))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("tubes/drains/lines");
        verify(clinicalHandoverRepository, never()).save(any());
    }

    @Test
    void initiateHandover_succeedsWhenReady() {
        Long hospitalId = 1L, bookingId = 5L;
        when(securityHelper.getCurrentHospitalId()).thenReturn(hospitalId);
        when(securityHelper.getCurrentUserEmail()).thenReturn("nurse.a@hospital.com");
        when(bookingRepository.findById(bookingId)).thenReturn(Optional.of(tenantBooking(bookingId, hospitalId, 10L)));
        when(pacuRecordRepository.findByOtBookingIdAndHospitalId(bookingId, hospitalId)).thenReturn(Optional.of(readyPacu(10)));
        when(clinicalHandoverRepository.findByOtBookingIdAndHospitalId(bookingId, hospitalId)).thenReturn(Optional.empty());
        when(clinicalHandoverRepository.save(any(ClinicalHandover.class))).thenAnswer(inv -> inv.getArgument(0));

        ClinicalHandover saved = otService.initiateHandover(bookingId, handoverRequest());

        assertThat(saved.getStatus()).isEqualTo("PENDING");
        assertThat(saved.getHandoverBy()).isEqualTo("nurse.a@hospital.com");
        assertThat(saved.getTransferTime()).isNotNull();
    }

    @Test
    void acceptHandover_locksRecord() {
        Long hospitalId = 1L, bookingId = 5L;
        when(securityHelper.getCurrentHospitalId()).thenReturn(hospitalId);
        when(securityHelper.getCurrentUserEmail()).thenReturn("ward.nurse@hospital.com");
        when(bookingRepository.findById(bookingId)).thenReturn(Optional.of(tenantBooking(bookingId, hospitalId, 10L)));
        ClinicalHandover handover = new ClinicalHandover();
        handover.setStatus("PENDING");
        when(clinicalHandoverRepository.findByOtBookingIdAndHospitalId(bookingId, hospitalId)).thenReturn(Optional.of(handover));
        when(clinicalHandoverRepository.save(any(ClinicalHandover.class))).thenAnswer(inv -> inv.getArgument(0));

        ClinicalHandover accepted = otService.acceptHandover(bookingId);

        assertThat(accepted.getStatus()).isEqualTo("ACCEPTED");
        assertThat(accepted.getAcceptedBy()).isEqualTo("ward.nurse@hospital.com");
        assertThat(accepted.getAcceptedTime()).isNotNull();
    }

    @Test
    void updateHandover_rejectedAfterAccepted() {
        Long hospitalId = 1L, bookingId = 5L;
        when(securityHelper.getCurrentHospitalId()).thenReturn(hospitalId);
        when(bookingRepository.findById(bookingId)).thenReturn(Optional.of(tenantBooking(bookingId, hospitalId, 10L)));
        ClinicalHandover handover = new ClinicalHandover();
        handover.setStatus("ACCEPTED");
        when(clinicalHandoverRepository.findByOtBookingIdAndHospitalId(bookingId, hospitalId)).thenReturn(Optional.of(handover));

        assertThatThrownBy(() -> otService.updateHandover(bookingId, handoverRequest()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("locked");
        verify(clinicalHandoverRepository, never()).save(any());
    }

    @Test
    void initiateHandover_rejectsCrossTenant() {
        Long hospitalId = 1L, bookingId = 5L;
        when(securityHelper.getCurrentHospitalId()).thenReturn(hospitalId);
        when(bookingRepository.findById(bookingId)).thenReturn(Optional.of(tenantBooking(bookingId, 99L, 10L)));

        assertThatThrownBy(() -> otService.initiateHandover(bookingId, handoverRequest()))
                .isInstanceOf(UnauthorizedException.class);
        verify(clinicalHandoverRepository, never()).save(any());
    }

    // ===== Post-operative Orders (Form 21) =====

    @Test
    void savePostopOrders_createsDraft() {
        Long hospitalId = 1L, bookingId = 5L;
        when(securityHelper.getCurrentHospitalId()).thenReturn(hospitalId);
        when(bookingRepository.findById(bookingId)).thenReturn(Optional.of(tenantBooking(bookingId, hospitalId, 10L)));
        when(postopOrdersRepository.findByOtBookingIdAndHospitalId(bookingId, hospitalId)).thenReturn(Optional.empty());
        when(postopOrdersRepository.save(any(PostopOrders.class))).thenAnswer(inv -> inv.getArgument(0));

        com.hms.dto.PostopOrdersRequest req = new com.hms.dto.PostopOrdersRequest();
        req.setPostopDiagnosis("Acute appendicitis, resolved");
        req.setDietOrder("NPO 6h then clear fluids");
        PostopOrders saved = otService.savePostopOrders(bookingId, req);

        assertThat(saved.getStatus()).isEqualTo("DRAFT");
        assertThat(saved.getSurgeonId()).isEqualTo(7L);
        assertThat(saved.getPostopDiagnosis()).isEqualTo("Acute appendicitis, resolved");
    }

    @Test
    void signPostopOrders_rejectedWithoutDiagnosis() {
        Long hospitalId = 1L, bookingId = 5L;
        when(securityHelper.getCurrentHospitalId()).thenReturn(hospitalId);
        when(securityHelper.getCurrentUserEmail()).thenReturn("surgeon@hospital.com");
        when(bookingRepository.findById(bookingId)).thenReturn(Optional.of(tenantBooking(bookingId, hospitalId, 10L)));
        PostopOrders orders = new PostopOrders();
        orders.setStatus("DRAFT");
        orders.setPostopDiagnosis(null);
        when(postopOrdersRepository.findByOtBookingIdAndHospitalId(bookingId, hospitalId)).thenReturn(Optional.of(orders));

        assertThatThrownBy(() -> otService.signPostopOrders(bookingId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("diagnosis");
        verify(postopOrdersRepository, never()).save(any());
    }

    @Test
    void signPostopOrders_stampsSignatureAndLocks() {
        Long hospitalId = 1L, bookingId = 5L;
        when(securityHelper.getCurrentHospitalId()).thenReturn(hospitalId);
        when(securityHelper.getCurrentUserEmail()).thenReturn("surgeon@hospital.com");
        when(bookingRepository.findById(bookingId)).thenReturn(Optional.of(tenantBooking(bookingId, hospitalId, 10L)));
        PostopOrders orders = new PostopOrders();
        orders.setStatus("DRAFT");
        orders.setPostopDiagnosis("Acute appendicitis, resolved");
        when(postopOrdersRepository.findByOtBookingIdAndHospitalId(bookingId, hospitalId)).thenReturn(Optional.of(orders));
        when(postopOrdersRepository.save(any(PostopOrders.class))).thenAnswer(inv -> inv.getArgument(0));

        PostopOrders signed = otService.signPostopOrders(bookingId);

        assertThat(signed.getStatus()).isEqualTo("SIGNED");
        assertThat(signed.getSignedBy()).isEqualTo("surgeon@hospital.com");
        assertThat(signed.getSignedAt()).isNotNull();
    }

    @Test
    void savePostopOrders_rejectedAfterSigned() {
        Long hospitalId = 1L, bookingId = 5L;
        when(securityHelper.getCurrentHospitalId()).thenReturn(hospitalId);
        when(bookingRepository.findById(bookingId)).thenReturn(Optional.of(tenantBooking(bookingId, hospitalId, 10L)));
        PostopOrders orders = new PostopOrders();
        orders.setStatus("SIGNED");
        when(postopOrdersRepository.findByOtBookingIdAndHospitalId(bookingId, hospitalId)).thenReturn(Optional.of(orders));

        assertThatThrownBy(() -> otService.savePostopOrders(bookingId, new com.hms.dto.PostopOrdersRequest()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("read-only");
        verify(postopOrdersRepository, never()).save(any());
    }

    @Test
    void savePostopOrders_rejectsCrossTenant() {
        Long hospitalId = 1L, bookingId = 5L;
        when(securityHelper.getCurrentHospitalId()).thenReturn(hospitalId);
        when(bookingRepository.findById(bookingId)).thenReturn(Optional.of(tenantBooking(bookingId, 99L, 10L)));

        assertThatThrownBy(() -> otService.savePostopOrders(bookingId, new com.hms.dto.PostopOrdersRequest()))
                .isInstanceOf(UnauthorizedException.class);
        verify(postopOrdersRepository, never()).save(any());
    }

    // ===== Instrument Count (Form 23) =====

    private OtChecklist signedThroughTimeOut() {
        OtChecklist checklist = new OtChecklist();
        checklist.setSignInCompleted(true);
        checklist.setTimeOutCompleted(true);
        return checklist;
    }

    private com.hms.dto.OtChecklistRequest signOutRequest() {
        com.hms.dto.OtChecklistRequest req = new com.hms.dto.OtChecklistRequest();
        req.setPhase("SIGN_OUT");
        return req;
    }

    @Test
    void signOut_blockedWhenCountPending() {
        Long hospitalId = 1L, bookingId = 5L;
        when(securityHelper.getCurrentHospitalId()).thenReturn(hospitalId);
        when(securityHelper.getCurrentUserEmail()).thenReturn("nurse@hospital.com");
        when(bookingRepository.findById(bookingId)).thenReturn(Optional.of(tenantBooking(bookingId, hospitalId, 10L)));
        when(checklistRepository.findByOtBookingIdAndHospitalId(bookingId, hospitalId)).thenReturn(Optional.of(signedThroughTimeOut()));
        OtInstrumentCount count = new OtInstrumentCount();
        count.setFinalCountStatus("PENDING");
        when(instrumentCountRepository.findByOtBookingIdAndHospitalId(bookingId, hospitalId)).thenReturn(Optional.of(count));

        assertThatThrownBy(() -> otService.signChecklist(bookingId, signOutRequest()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("count");
        verify(checklistRepository, never()).save(any());
    }

    @Test
    void signOut_blockedWhenMismatchUnresolved() {
        Long hospitalId = 1L, bookingId = 5L;
        when(securityHelper.getCurrentHospitalId()).thenReturn(hospitalId);
        when(securityHelper.getCurrentUserEmail()).thenReturn("nurse@hospital.com");
        when(bookingRepository.findById(bookingId)).thenReturn(Optional.of(tenantBooking(bookingId, hospitalId, 10L)));
        when(checklistRepository.findByOtBookingIdAndHospitalId(bookingId, hospitalId)).thenReturn(Optional.of(signedThroughTimeOut()));
        OtInstrumentCount count = new OtInstrumentCount();
        count.setFinalCountStatus("MISMATCH");
        count.setResolved(false);
        when(instrumentCountRepository.findByOtBookingIdAndHospitalId(bookingId, hospitalId)).thenReturn(Optional.of(count));

        assertThatThrownBy(() -> otService.signChecklist(bookingId, signOutRequest()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("discrepancy");
        verify(checklistRepository, never()).save(any());
    }

    @Test
    void signOut_allowedWhenMismatchResolved() {
        Long hospitalId = 1L, bookingId = 5L;
        when(securityHelper.getCurrentHospitalId()).thenReturn(hospitalId);
        when(securityHelper.getCurrentUserEmail()).thenReturn("nurse@hospital.com");
        when(bookingRepository.findById(bookingId)).thenReturn(Optional.of(tenantBooking(bookingId, hospitalId, 10L)));
        when(checklistRepository.findByOtBookingIdAndHospitalId(bookingId, hospitalId)).thenReturn(Optional.of(signedThroughTimeOut()));
        OtInstrumentCount count = new OtInstrumentCount();
        count.setFinalCountStatus("MISMATCH");
        count.setResolved(true);
        when(instrumentCountRepository.findByOtBookingIdAndHospitalId(bookingId, hospitalId)).thenReturn(Optional.of(count));
        when(checklistRepository.save(any(OtChecklist.class))).thenAnswer(inv -> inv.getArgument(0));
        when(bookingRepository.save(any(OtBooking.class))).thenAnswer(inv -> inv.getArgument(0));

        OtChecklist saved = otService.signChecklist(bookingId, signOutRequest());

        assertThat(saved.isSignOutCompleted()).isTrue();
    }

    @Test
    void saveInstrumentCount_mismatchResetsResolution() {
        Long hospitalId = 1L, bookingId = 5L;
        when(securityHelper.getCurrentHospitalId()).thenReturn(hospitalId);
        when(bookingRepository.findById(bookingId)).thenReturn(Optional.of(tenantBooking(bookingId, hospitalId, 10L)));
        OtInstrumentCount existing = new OtInstrumentCount();
        existing.setResolved(true); // earlier discrepancy was resolved
        when(instrumentCountRepository.findByOtBookingIdAndHospitalId(bookingId, hospitalId)).thenReturn(Optional.of(existing));
        when(instrumentCountRepository.save(any(OtInstrumentCount.class))).thenAnswer(inv -> inv.getArgument(0));

        com.hms.dto.InstrumentCountRequest req = new com.hms.dto.InstrumentCountRequest();
        req.setFinalCountStatus("MISMATCH");
        req.setCountSummary("Abdominal sponges 9/10");
        OtInstrumentCount saved = otService.saveInstrumentCount(bookingId, req);

        assertThat(saved.getFinalCountStatus()).isEqualTo("MISMATCH");
        assertThat(saved.getDiscrepancyFound()).isTrue();
        assertThat(saved.getResolved()).isFalse(); // fresh mismatch requires fresh documentation
    }

    @Test
    void resolveInstrumentCount_requiresSearchAndRemarks() {
        Long hospitalId = 1L, bookingId = 5L;
        when(securityHelper.getCurrentHospitalId()).thenReturn(hospitalId);
        when(securityHelper.getCurrentUserEmail()).thenReturn("nurse@hospital.com");
        when(bookingRepository.findById(bookingId)).thenReturn(Optional.of(tenantBooking(bookingId, hospitalId, 10L)));
        OtInstrumentCount count = new OtInstrumentCount();
        count.setFinalCountStatus("MISMATCH");
        when(instrumentCountRepository.findByOtBookingIdAndHospitalId(bookingId, hospitalId)).thenReturn(Optional.of(count));

        com.hms.dto.InstrumentCountRequest noSearch = new com.hms.dto.InstrumentCountRequest();
        noSearch.setResolutionRemarks("Sponge located in kick bucket");
        assertThatThrownBy(() -> otService.resolveInstrumentCount(bookingId, noSearch))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("search");
        verify(instrumentCountRepository, never()).save(any());
    }

    @Test
    void resolveInstrumentCount_documentsAndUnblocks() {
        Long hospitalId = 1L, bookingId = 5L;
        when(securityHelper.getCurrentHospitalId()).thenReturn(hospitalId);
        when(securityHelper.getCurrentUserEmail()).thenReturn("nurse@hospital.com");
        when(bookingRepository.findById(bookingId)).thenReturn(Optional.of(tenantBooking(bookingId, hospitalId, 10L)));
        OtInstrumentCount count = new OtInstrumentCount();
        count.setFinalCountStatus("MISMATCH");
        when(instrumentCountRepository.findByOtBookingIdAndHospitalId(bookingId, hospitalId)).thenReturn(Optional.of(count));
        when(instrumentCountRepository.save(any(OtInstrumentCount.class))).thenAnswer(inv -> inv.getArgument(0));

        com.hms.dto.InstrumentCountRequest req = new com.hms.dto.InstrumentCountRequest();
        req.setSearchConducted(true);
        req.setXrayPerformed(true);
        req.setResolutionRemarks("X-ray negative; sponge located in kick bucket");
        OtInstrumentCount resolved = otService.resolveInstrumentCount(bookingId, req);

        assertThat(resolved.getResolved()).isTrue();
        assertThat(resolved.getXrayPerformed()).isTrue();
        assertThat(resolved.getCompletedAt()).isNotNull();
    }

    @Test
    void saveInstrumentCount_rejectsCrossTenant() {
        Long hospitalId = 1L, bookingId = 5L;
        when(securityHelper.getCurrentHospitalId()).thenReturn(hospitalId);
        when(bookingRepository.findById(bookingId)).thenReturn(Optional.of(tenantBooking(bookingId, 99L, 10L)));

        assertThatThrownBy(() -> otService.saveInstrumentCount(bookingId, new com.hms.dto.InstrumentCountRequest()))
                .isInstanceOf(UnauthorizedException.class);
        verify(instrumentCountRepository, never()).save(any());
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
    // ===== Implant Record (Form 24) =====

    @Test
    void addImplant_blockedWhenNoOperationRecord() {
        Long hospitalId = 1L, bookingId = 5L;
        when(securityHelper.getCurrentHospitalId()).thenReturn(hospitalId);
        when(bookingRepository.findById(bookingId)).thenReturn(Optional.of(tenantBooking(bookingId, hospitalId, 10L)));
        when(operationRecordRepository.findByOtBookingIdAndHospitalId(bookingId, hospitalId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> otService.addImplant(bookingId, new com.hms.dto.ImplantRecordRequest()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("operation record must be created");
        verify(implantRepository, never()).save(any());
    }

    @Test
    void addImplant_blockedWhenOperationRecordFinalized() {
        Long hospitalId = 1L, bookingId = 5L;
        when(securityHelper.getCurrentHospitalId()).thenReturn(hospitalId);
        when(bookingRepository.findById(bookingId)).thenReturn(Optional.of(tenantBooking(bookingId, hospitalId, 10L)));
        OperationRecord opRec = new OperationRecord();
        opRec.setStatus("FINALIZED");
        when(operationRecordRepository.findByOtBookingIdAndHospitalId(bookingId, hospitalId))
                .thenReturn(Optional.of(opRec));

        assertThatThrownBy(() -> otService.addImplant(bookingId, new com.hms.dto.ImplantRecordRequest()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("finalized operation record");
        verify(implantRepository, never()).save(any());
    }

    @Test
    void addImplant_blockedOnDuplicateSerialNumber() {
        Long hospitalId = 1L, bookingId = 5L;
        when(securityHelper.getCurrentHospitalId()).thenReturn(hospitalId);
        when(bookingRepository.findById(bookingId)).thenReturn(Optional.of(tenantBooking(bookingId, hospitalId, 10L)));
        OperationRecord opRec = new OperationRecord();
        opRec.setStatus("DRAFT");
        when(operationRecordRepository.findByOtBookingIdAndHospitalId(bookingId, hospitalId))
                .thenReturn(Optional.of(opRec));

        PatientImplant existing = new PatientImplant();
        existing.setSerialNumber("SN-DUPE");
        when(implantRepository.findBySerialNumberAndHospitalId("SN-DUPE", hospitalId))
                .thenReturn(java.util.List.of(existing));

        com.hms.dto.ImplantRecordRequest req = new com.hms.dto.ImplantRecordRequest();
        req.setSerialNumber("SN-DUPE");

        assertThatThrownBy(() -> otService.addImplant(bookingId, req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("already registered");
        verify(implantRepository, never()).save(any());
    }

    @Test
    void addImplant_rejectsCrossTenant() {
        Long hospitalId = 1L, bookingId = 5L;
        when(securityHelper.getCurrentHospitalId()).thenReturn(hospitalId);
        // Booking belongs to hospital 99
        when(bookingRepository.findById(bookingId)).thenReturn(Optional.of(tenantBooking(bookingId, 99L, 10L)));

        assertThatThrownBy(() -> otService.addImplant(bookingId, new com.hms.dto.ImplantRecordRequest()))
                .isInstanceOf(com.hms.exception.UnauthorizedException.class);
        verify(implantRepository, never()).save(any());
    }

    @Test
    void signImplant_blocksDoubleSign() {
        Long hospitalId = 1L, bookingId = 5L, implantId = 20L;
        when(securityHelper.getCurrentHospitalId()).thenReturn(hospitalId);
        PatientImplant implant = new PatientImplant();
        implant.setId(implantId);
        implant.setHospitalId(hospitalId);
        implant.setOtBookingId(bookingId);
        implant.setStatus("SIGNED");
        when(implantRepository.findById(implantId)).thenReturn(Optional.of(implant));

        assertThatThrownBy(() -> otService.signImplant(bookingId, implantId, "sig-data"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("already signed");
        verify(implantRepository, never()).save(any());
    }

    // ===== OT Readiness Gating (Form 26) =====

    @Test
    void scheduleBooking_succeedsWhenNoReadinessRecord() {
        Long hospitalId = 1L;
        Long admissionId = 10L;
        java.time.LocalDateTime testTime = java.time.LocalDateTime.now().plusDays(2);
        java.time.LocalDate testDate = testTime.toLocalDate();

        when(securityHelper.getCurrentHospitalId()).thenReturn(hospitalId);

        IpdAdmission admission = new IpdAdmission();
        admission.setId(admissionId);
        admission.setHospitalId(hospitalId);
        when(ipdAdmissionRepository.findById(admissionId)).thenReturn(Optional.of(admission));

        // Mock readiness: empty (doesn't exist) -> should pass do-no-harm logic
        when(readinessRepository.findByOtRoomAndReadinessDateAndHospitalId("OT 1", testDate, hospitalId))
                .thenReturn(Optional.empty());

        when(bookingRepository.findByHospitalIdOrderByScheduledDateTimeDesc(hospitalId))
                .thenReturn(new java.util.ArrayList<>());
        when(bookingRepository.save(any(OtBooking.class))).thenAnswer(inv -> inv.getArgument(0));

        OtBookingRequest req = new OtBookingRequest(
                "Cholecystectomy", testTime, 5L, "Dr. Ana", "OT 1", "Scheduled surgery"
        );

        OtBooking booking = otService.scheduleBooking(admissionId, req);
        assertThat(booking).isNotNull();
        assertThat(booking.getProcedureName()).isEqualTo("Cholecystectomy");
    }

    @Test
    void scheduleBooking_failsWhenReadinessNotReady() {
        Long hospitalId = 1L;
        Long admissionId = 10L;
        java.time.LocalDateTime testTime = java.time.LocalDateTime.now().plusDays(2);
        java.time.LocalDate testDate = testTime.toLocalDate();

        when(securityHelper.getCurrentHospitalId()).thenReturn(hospitalId);

        IpdAdmission admission = new IpdAdmission();
        admission.setId(admissionId);
        admission.setHospitalId(hospitalId);
        when(ipdAdmissionRepository.findById(admissionId)).thenReturn(Optional.of(admission));

        // Mock readiness: exists and PENDING -> should throw
        OtReadiness readiness = new OtReadiness();
        readiness.setOtRoom("OT 1");
        readiness.setReadinessDate(testDate);
        readiness.setStatus("PENDING");
        when(readinessRepository.findByOtRoomAndReadinessDateAndHospitalId("OT 1", testDate, hospitalId))
                .thenReturn(Optional.of(readiness));

        OtBookingRequest req = new OtBookingRequest(
                "Cholecystectomy", testTime, 5L, "Dr. Ana", "OT 1", "Scheduled surgery"
        );

        assertThatThrownBy(() -> otService.scheduleBooking(admissionId, req))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("is not READY");
        verify(bookingRepository, never()).save(any());
    }

    @Test
    void scheduleBooking_succeedsWhenReadinessReady() {
        Long hospitalId = 1L;
        Long admissionId = 10L;
        java.time.LocalDateTime testTime = java.time.LocalDateTime.now().plusDays(2);
        java.time.LocalDate testDate = testTime.toLocalDate();

        when(securityHelper.getCurrentHospitalId()).thenReturn(hospitalId);

        IpdAdmission admission = new IpdAdmission();
        admission.setId(admissionId);
        admission.setHospitalId(hospitalId);
        when(ipdAdmissionRepository.findById(admissionId)).thenReturn(Optional.of(admission));

        // Mock readiness: exists and READY -> should pass
        OtReadiness readiness = new OtReadiness();
        readiness.setOtRoom("OT 1");
        readiness.setReadinessDate(testDate);
        readiness.setStatus("READY");
        when(readinessRepository.findByOtRoomAndReadinessDateAndHospitalId("OT 1", testDate, hospitalId))
                .thenReturn(Optional.of(readiness));

        when(bookingRepository.findByHospitalIdOrderByScheduledDateTimeDesc(hospitalId))
                .thenReturn(new java.util.ArrayList<>());
        when(bookingRepository.save(any(OtBooking.class))).thenAnswer(inv -> inv.getArgument(0));

        OtBookingRequest req = new OtBookingRequest(
                "Cholecystectomy", testTime, 5L, "Dr. Ana", "OT 1", "Scheduled surgery"
        );

        OtBooking booking = otService.scheduleBooking(admissionId, req);
        assertThat(booking).isNotNull();
        assertThat(booking.getStatus()).isEqualTo("SCHEDULED");
    }

    // ===== Pre-Anaesthesia Assessment / PAC (Form 15) =====

    private IpdAdmission tenantAdmission(Long admissionId, Long hospitalId) {
        IpdAdmission admission = new IpdAdmission();
        admission.setId(admissionId);
        admission.setHospitalId(hospitalId);
        admission.setPatientId(55L);
        return admission;
    }

    @Test
    void scheduleBooking_blockedWhenPacNotApproved() {
        Long hospitalId = 1L, admissionId = 10L;
        when(securityHelper.getCurrentHospitalId()).thenReturn(hospitalId);
        when(ipdAdmissionRepository.findById(admissionId)).thenReturn(Optional.of(tenantAdmission(admissionId, hospitalId)));
        PreAnaesthesiaAssessment pac = new PreAnaesthesiaAssessment();
        pac.setStatus("DRAFT");
        when(pacRepository.findByAdmissionIdAndHospitalId(admissionId, hospitalId)).thenReturn(Optional.of(pac));

        OtBookingRequest req = new OtBookingRequest(
                "Cholecystectomy", LocalDateTime.now().plusDays(2), 5L, "Dr. Ana", "OT 1", "Scheduled surgery");

        assertThatThrownBy(() -> otService.scheduleBooking(admissionId, req))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("pre-anaesthesia assessment");
        verify(bookingRepository, never()).save(any());
    }

    @Test
    void scheduleBooking_blockedWhenPacFitnessDeferred() {
        Long hospitalId = 1L, admissionId = 10L;
        when(securityHelper.getCurrentHospitalId()).thenReturn(hospitalId);
        when(ipdAdmissionRepository.findById(admissionId)).thenReturn(Optional.of(tenantAdmission(admissionId, hospitalId)));
        PreAnaesthesiaAssessment pac = new PreAnaesthesiaAssessment();
        pac.setStatus("APPROVED");
        pac.setFitnessStatus("DEFERRED");
        when(pacRepository.findByAdmissionIdAndHospitalId(admissionId, hospitalId)).thenReturn(Optional.of(pac));

        OtBookingRequest req = new OtBookingRequest(
                "Cholecystectomy", LocalDateTime.now().plusDays(2), 5L, "Dr. Ana", "OT 1", "Scheduled surgery");

        assertThatThrownBy(() -> otService.scheduleBooking(admissionId, req))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("DEFERRED");
        verify(bookingRepository, never()).save(any());
    }

    @Test
    void scheduleBooking_allowedWhenPacApprovedAndFit() {
        Long hospitalId = 1L, admissionId = 10L;
        Authentication authentication = mock(Authentication.class);
        when(authentication.getName()).thenReturn("doctor@hospital.com");
        SecurityContext securityContext = mock(SecurityContext.class);
        when(securityContext.getAuthentication()).thenReturn(authentication);
        SecurityContextHolder.setContext(securityContext);
        when(securityHelper.getCurrentHospitalId()).thenReturn(hospitalId);
        when(ipdAdmissionRepository.findById(admissionId)).thenReturn(Optional.of(tenantAdmission(admissionId, hospitalId)));
        PreAnaesthesiaAssessment pac = new PreAnaesthesiaAssessment();
        pac.setStatus("APPROVED");
        pac.setFitnessStatus("FIT_WITH_PRECAUTIONS");
        when(pacRepository.findByAdmissionIdAndHospitalId(admissionId, hospitalId)).thenReturn(Optional.of(pac));
        when(bookingRepository.findByHospitalIdOrderByScheduledDateTimeDesc(hospitalId)).thenReturn(new ArrayList<>());
        when(bookingRepository.save(any(OtBooking.class))).thenAnswer(inv -> inv.getArgument(0));

        OtBookingRequest req = new OtBookingRequest(
                "Cholecystectomy", LocalDateTime.now().plusDays(2), 5L, "Dr. Ana", "OT 1", "Scheduled surgery");

        OtBooking booking = otService.scheduleBooking(admissionId, req);

        assertThat(booking.getStatus()).isEqualTo("SCHEDULED");
    }

    @Test
    void approvePac_rejectedWithoutAsaClass() {
        Long hospitalId = 1L, admissionId = 10L;
        when(securityHelper.getCurrentHospitalId()).thenReturn(hospitalId);
        when(securityHelper.getCurrentUserEmail()).thenReturn("anaes@hospital.com");
        when(ipdAdmissionRepository.findById(admissionId)).thenReturn(Optional.of(tenantAdmission(admissionId, hospitalId)));
        PreAnaesthesiaAssessment pac = new PreAnaesthesiaAssessment();
        pac.setStatus("DRAFT");
        pac.setAsaClass(null);
        pac.setFitnessStatus("FIT");
        when(pacRepository.findByAdmissionIdAndHospitalId(admissionId, hospitalId)).thenReturn(Optional.of(pac));

        assertThatThrownBy(() -> otService.approvePac(admissionId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ASA");
        verify(pacRepository, never()).save(any());
    }

    @Test
    void approvePac_stampsSignature() {
        Long hospitalId = 1L, admissionId = 10L;
        when(securityHelper.getCurrentHospitalId()).thenReturn(hospitalId);
        when(securityHelper.getCurrentUserEmail()).thenReturn("anaes@hospital.com");
        when(ipdAdmissionRepository.findById(admissionId)).thenReturn(Optional.of(tenantAdmission(admissionId, hospitalId)));
        PreAnaesthesiaAssessment pac = new PreAnaesthesiaAssessment();
        pac.setStatus("DRAFT");
        pac.setAsaClass("II");
        pac.setFitnessStatus("FIT");
        when(pacRepository.findByAdmissionIdAndHospitalId(admissionId, hospitalId)).thenReturn(Optional.of(pac));
        when(pacRepository.save(any(PreAnaesthesiaAssessment.class))).thenAnswer(inv -> inv.getArgument(0));

        PreAnaesthesiaAssessment approved = otService.approvePac(admissionId);

        assertThat(approved.getStatus()).isEqualTo("APPROVED");
        assertThat(approved.getSignedBy()).isEqualTo("anaes@hospital.com");
        assertThat(approved.getSignedAt()).isNotNull();
    }

    @Test
    void savePac_rejectsCrossTenant() {
        Long hospitalId = 1L, admissionId = 10L;
        when(securityHelper.getCurrentHospitalId()).thenReturn(hospitalId);
        when(ipdAdmissionRepository.findById(admissionId)).thenReturn(Optional.of(tenantAdmission(admissionId, 99L)));

        assertThatThrownBy(() -> otService.savePac(admissionId, new com.hms.dto.PacRequest()))
                .isInstanceOf(UnauthorizedException.class);
        verify(pacRepository, never()).save(any());
    }

    // ===== Surgical Consent gate on WHO sign-in (Form 16) =====

    private com.hms.dto.OtChecklistRequest signInRequest() {
        com.hms.dto.OtChecklistRequest req = new com.hms.dto.OtChecklistRequest();
        req.setPhase("SIGN_IN");
        return req;
    }

    private PatientConsent surgeryConsent(String status) {
        PatientConsent c = new PatientConsent();
        c.setConsentType("SURGERY");
        c.setStatus(status);
        return c;
    }

    @Test
    void signIn_blockedWhenSurgicalConsentUnsigned() {
        Long hospitalId = 1L, bookingId = 5L;
        when(securityHelper.getCurrentHospitalId()).thenReturn(hospitalId);
        when(securityHelper.getCurrentUserEmail()).thenReturn("nurse@hospital.com");
        when(bookingRepository.findById(bookingId)).thenReturn(Optional.of(tenantBooking(bookingId, hospitalId, 10L)));
        when(checklistRepository.findByOtBookingIdAndHospitalId(bookingId, hospitalId)).thenReturn(Optional.of(new OtChecklist()));
        when(patientConsentRepository.findByHospitalIdAndAdmissionIdAndIsDeletedFalse(hospitalId, 10L))
                .thenReturn(java.util.List.of(surgeryConsent("DRAFT")));

        assertThatThrownBy(() -> otService.signChecklist(bookingId, signInRequest()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("surgical consent");
        verify(checklistRepository, never()).save(any());
    }

    @Test
    void signIn_allowedWhenSurgicalConsentSigned() {
        Long hospitalId = 1L, bookingId = 5L;
        when(securityHelper.getCurrentHospitalId()).thenReturn(hospitalId);
        when(securityHelper.getCurrentUserEmail()).thenReturn("nurse@hospital.com");
        when(bookingRepository.findById(bookingId)).thenReturn(Optional.of(tenantBooking(bookingId, hospitalId, 10L)));
        when(checklistRepository.findByOtBookingIdAndHospitalId(bookingId, hospitalId)).thenReturn(Optional.of(new OtChecklist()));
        when(patientConsentRepository.findByHospitalIdAndAdmissionIdAndIsDeletedFalse(hospitalId, 10L))
                .thenReturn(java.util.List.of(surgeryConsent("SIGNED")));
        when(checklistRepository.save(any(OtChecklist.class))).thenAnswer(inv -> inv.getArgument(0));

        OtChecklist saved = otService.signChecklist(bookingId, signInRequest());

        assertThat(saved.isSignInCompleted()).isTrue();
    }

    @Test
    void updateStatus_inProgress_blockedWhenRoomNotReady() {
        Long hospitalId = 1L, bookingId = 5L;
        when(securityHelper.getCurrentHospitalId()).thenReturn(hospitalId);
        
        OtBooking booking = tenantBooking(bookingId, hospitalId, 10L);
        booking.setOtRoomNumber("OT 1");
        when(bookingRepository.findById(bookingId)).thenReturn(Optional.of(booking));

        OtReadiness readiness = new OtReadiness();
        readiness.setOtRoom("OT 1");
        readiness.setReadinessDate(java.time.LocalDate.now());
        readiness.setStatus("PENDING");
        
        when(readinessRepository.findByOtRoomAndReadinessDateAndHospitalId("OT 1", java.time.LocalDate.now(), hospitalId))
                .thenReturn(Optional.of(readiness));

        assertThatThrownBy(() -> otService.updateStatus(bookingId, "IN_PROGRESS"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("is not READY");
        verify(bookingRepository, never()).save(any());
    }

    @Test
    void updateStatus_inProgress_allowedWhenRoomReady() {
        Long hospitalId = 1L, bookingId = 5L;
        when(securityHelper.getCurrentHospitalId()).thenReturn(hospitalId);
        
        OtBooking booking = tenantBooking(bookingId, hospitalId, 10L);
        booking.setOtRoomNumber("OT 1");
        when(bookingRepository.findById(bookingId)).thenReturn(Optional.of(booking));

        OtReadiness readiness = new OtReadiness();
        readiness.setOtRoom("OT 1");
        readiness.setReadinessDate(java.time.LocalDate.now());
        readiness.setStatus("READY");
        
        when(readinessRepository.findByOtRoomAndReadinessDateAndHospitalId("OT 1", java.time.LocalDate.now(), hospitalId))
                .thenReturn(Optional.of(readiness));
        when(bookingRepository.save(any(OtBooking.class))).thenAnswer(inv -> inv.getArgument(0));

        OtBooking saved = otService.updateStatus(bookingId, "IN_PROGRESS");
        assertThat(saved.getStatus()).isEqualTo("IN_PROGRESS");
    }

    @Test
    void updateStatus_inProgress_allowedWhenNoReadinessRecord() {
        Long hospitalId = 1L, bookingId = 5L;
        when(securityHelper.getCurrentHospitalId()).thenReturn(hospitalId);
        
        OtBooking booking = tenantBooking(bookingId, hospitalId, 10L);
        booking.setOtRoomNumber("OT 1");
        when(bookingRepository.findById(bookingId)).thenReturn(Optional.of(booking));

        when(readinessRepository.findByOtRoomAndReadinessDateAndHospitalId("OT 1", java.time.LocalDate.now(), hospitalId))
                .thenReturn(Optional.empty());
        when(bookingRepository.save(any(OtBooking.class))).thenAnswer(inv -> inv.getArgument(0));

        OtBooking saved = otService.updateStatus(bookingId, "IN_PROGRESS");
        assertThat(saved.getStatus()).isEqualTo("IN_PROGRESS");
    }
}

