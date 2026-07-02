package com.hms.service;

import com.hms.dto.*;
import com.hms.entity.*;
import com.hms.repository.*;
import com.hms.security.SecurityContextHelper;
import com.hms.service.hospital.TrainingService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TrainingServiceTest {

    @Mock private TrainingMasterRepository masterRepository;
    @Mock private TrainingSessionRepository sessionRepository;
    @Mock private TrainingAttendanceRepository attendanceRepository;
    @Mock private TrainingCertificationRepository certificationRepository;
    @Mock private EmployeeRepository employeeRepository;
    @Mock private SecurityContextHelper securityHelper;
    @Mock private AuditLogRepository auditLogRepository;

    @InjectMocks
    private TrainingService service;

    private static final Long HOSPITAL_ID = 1L;

    private TrainingSession session(String status) {
        TrainingSession s = new TrainingSession();
        s.setId(5L);
        s.setHospitalId(HOSPITAL_ID);
        s.setTrainingMasterId(2L);
        s.setStatus(status);
        return s;
    }

    private Employee employee() {
        Employee e = new Employee();
        e.setId(10L);
        e.setHospitalId(HOSPITAL_ID);
        e.setEmployeeCode("EMP-1-1");
        e.setDepartment("Nursing");
        return e;
    }

    // ===== BR-2: employee must belong to the hospital =====

    @Test
    void markAttendance_rejectedWhenEmployeeNotFound() {
        when(securityHelper.getCurrentHospitalId()).thenReturn(HOSPITAL_ID);
        when(sessionRepository.findByIdAndHospitalId(5L, HOSPITAL_ID)).thenReturn(Optional.of(session("PLANNED")));
        when(employeeRepository.findByIdAndHospitalId(10L, HOSPITAL_ID)).thenReturn(Optional.empty());

        TrainingAttendanceMarkRequest req = new TrainingAttendanceMarkRequest();
        req.setSessionId(5L);
        req.setEmployeeId(10L);
        req.setAttendanceStatus("PRESENT");

        assertThatThrownBy(() -> service.markAttendance(req))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("BR-2");
        verify(attendanceRepository, never()).save(any());
    }

    // ===== BR-3: check-out must follow check-in =====

    @Test
    void markAttendance_rejectedWhenCheckOutBeforeCheckIn() {
        when(securityHelper.getCurrentHospitalId()).thenReturn(HOSPITAL_ID);
        when(sessionRepository.findByIdAndHospitalId(5L, HOSPITAL_ID)).thenReturn(Optional.of(session("PLANNED")));
        when(employeeRepository.findByIdAndHospitalId(10L, HOSPITAL_ID)).thenReturn(Optional.of(employee()));

        TrainingAttendanceMarkRequest req = new TrainingAttendanceMarkRequest();
        req.setSessionId(5L);
        req.setEmployeeId(10L);
        req.setAttendanceStatus("PRESENT");
        req.setCheckInTime(LocalDateTime.now());
        req.setCheckOutTime(LocalDateTime.now().minusHours(1));

        assertThatThrownBy(() -> service.markAttendance(req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Check-out");
    }

    // ===== BR-4: auto-start on first mark; post-hoc requires remark =====

    @Test
    void markAttendance_autoStartsPlannedSession() {
        when(securityHelper.getCurrentHospitalId()).thenReturn(HOSPITAL_ID);
        when(securityHelper.getCurrentUserEmail()).thenReturn("hr@hospital.com");
        TrainingSession s = session("PLANNED");
        when(sessionRepository.findByIdAndHospitalId(5L, HOSPITAL_ID)).thenReturn(Optional.of(s));
        when(employeeRepository.findByIdAndHospitalId(10L, HOSPITAL_ID)).thenReturn(Optional.of(employee()));
        when(attendanceRepository.findByHospitalIdAndSessionIdAndEmployeeId(HOSPITAL_ID, 5L, 10L)).thenReturn(Optional.empty());
        when(attendanceRepository.save(any(TrainingAttendance.class))).thenAnswer(i -> i.getArgument(0));

        TrainingAttendanceMarkRequest req = new TrainingAttendanceMarkRequest();
        req.setSessionId(5L);
        req.setEmployeeId(10L);
        req.setAttendanceStatus("present");

        TrainingAttendance saved = service.markAttendance(req);

        assertThat(saved.getAttendanceStatus()).isEqualTo("PRESENT");
        assertThat(s.getStatus()).isEqualTo("IN_PROGRESS");
    }

    @Test
    void markAttendance_onCompletedSessionRequiresRemark() {
        when(securityHelper.getCurrentHospitalId()).thenReturn(HOSPITAL_ID);
        when(sessionRepository.findByIdAndHospitalId(5L, HOSPITAL_ID)).thenReturn(Optional.of(session("COMPLETED")));
        when(employeeRepository.findByIdAndHospitalId(10L, HOSPITAL_ID)).thenReturn(Optional.of(employee()));
        when(attendanceRepository.findByHospitalIdAndSessionIdAndEmployeeId(HOSPITAL_ID, 5L, 10L)).thenReturn(Optional.empty());

        TrainingAttendanceMarkRequest req = new TrainingAttendanceMarkRequest();
        req.setSessionId(5L);
        req.setEmployeeId(10L);
        req.setAttendanceStatus("PRESENT");

        assertThatThrownBy(() -> service.markAttendance(req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("BR-4");
    }

    @Test
    void markAttendance_rejectedWhenAlreadyRecorded() {
        when(securityHelper.getCurrentHospitalId()).thenReturn(HOSPITAL_ID);
        when(sessionRepository.findByIdAndHospitalId(5L, HOSPITAL_ID)).thenReturn(Optional.of(session("IN_PROGRESS")));
        when(employeeRepository.findByIdAndHospitalId(10L, HOSPITAL_ID)).thenReturn(Optional.of(employee()));
        when(attendanceRepository.findByHospitalIdAndSessionIdAndEmployeeId(HOSPITAL_ID, 5L, 10L))
                .thenReturn(Optional.of(new TrainingAttendance()));

        TrainingAttendanceMarkRequest req = new TrainingAttendanceMarkRequest();
        req.setSessionId(5L);
        req.setEmployeeId(10L);
        req.setAttendanceStatus("PRESENT");

        assertThatThrownBy(() -> service.markAttendance(req))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("already been recorded");
    }

    // ===== BR-5/BR-6: verification certifies PRESENT attendees with expiry =====

    @Test
    void verifySession_certifiesPresentAttendeesWithExpiry() {
        when(securityHelper.getCurrentHospitalId()).thenReturn(HOSPITAL_ID);
        when(securityHelper.getCurrentUserEmail()).thenReturn("hr@hospital.com");
        TrainingSession s = session("IN_PROGRESS");
        when(sessionRepository.findByIdAndHospitalId(5L, HOSPITAL_ID)).thenReturn(Optional.of(s));
        when(sessionRepository.save(any(TrainingSession.class))).thenAnswer(i -> i.getArgument(0));

        TrainingMaster master = new TrainingMaster();
        master.setId(2L);
        master.setHospitalId(HOSPITAL_ID);
        master.setTitle("Fire Safety");
        master.setValidityPeriod(12);
        when(masterRepository.findByIdAndHospitalId(2L, HOSPITAL_ID)).thenReturn(Optional.of(master));

        TrainingAttendance present = new TrainingAttendance();
        present.setId(30L);
        present.setEmployeeId(10L);
        present.setAttendanceStatus("PRESENT");
        TrainingAttendance absent = new TrainingAttendance();
        absent.setId(31L);
        absent.setEmployeeId(11L);
        absent.setAttendanceStatus("ABSENT");
        when(attendanceRepository.findByHospitalIdAndSessionId(HOSPITAL_ID, 5L)).thenReturn(List.of(present, absent));
        when(attendanceRepository.save(any(TrainingAttendance.class))).thenAnswer(i -> i.getArgument(0));
        when(certificationRepository.findByHospitalIdAndEmployeeIdAndTrainingMasterId(HOSPITAL_ID, 10L, 2L)).thenReturn(Optional.empty());
        when(certificationRepository.save(any(TrainingCertification.class))).thenAnswer(i -> i.getArgument(0));

        TrainingSession saved = service.verifySession(5L);

        assertThat(saved.getStatus()).isEqualTo("COMPLETED");
        assertThat(present.isVerified()).isTrue();
        assertThat(absent.isVerified()).isFalse();
        verify(certificationRepository, times(1)).save(any(TrainingCertification.class));
    }

    @Test
    void verifySession_rejectedForCancelledSession() {
        when(securityHelper.getCurrentHospitalId()).thenReturn(HOSPITAL_ID);
        when(sessionRepository.findByIdAndHospitalId(5L, HOSPITAL_ID)).thenReturn(Optional.of(session("CANCELLED")));

        assertThatThrownBy(() -> service.verifySession(5L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("cancelled");
    }

    // ===== BR-7: correction requires reason and is audited =====

    @Test
    void correctAttendance_rejectedWithoutReason() {
        when(securityHelper.getCurrentHospitalId()).thenReturn(HOSPITAL_ID);
        TrainingAttendance existing = new TrainingAttendance();
        existing.setId(30L);
        existing.setHospitalId(HOSPITAL_ID);
        existing.setAttendanceStatus("ABSENT");
        when(attendanceRepository.findByIdAndHospitalId(30L, HOSPITAL_ID)).thenReturn(Optional.of(existing));

        TrainingAttendanceCorrectRequest req = new TrainingAttendanceCorrectRequest();
        req.setAttendanceStatus("PRESENT");

        assertThatThrownBy(() -> service.correctAttendance(30L, req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("BR-7");
        verify(attendanceRepository, never()).save(any());
    }

    @Test
    void correctAttendance_appliesChangeWithReason() {
        when(securityHelper.getCurrentHospitalId()).thenReturn(HOSPITAL_ID);
        TrainingAttendance existing = new TrainingAttendance();
        existing.setId(30L);
        existing.setHospitalId(HOSPITAL_ID);
        existing.setAttendanceStatus("ABSENT");
        when(attendanceRepository.findByIdAndHospitalId(30L, HOSPITAL_ID)).thenReturn(Optional.of(existing));
        when(attendanceRepository.save(any(TrainingAttendance.class))).thenAnswer(i -> i.getArgument(0));

        TrainingAttendanceCorrectRequest req = new TrainingAttendanceCorrectRequest();
        req.setAttendanceStatus("PRESENT");
        req.setReason("Sign-in sheet review found employee was present");

        TrainingAttendance saved = service.correctAttendance(30L, req);

        assertThat(saved.getAttendanceStatus()).isEqualTo("PRESENT");
    }

    // ===== BR-6: certification expiry sweep =====

    @Test
    void getCertifications_flagsExpiredAndExpiringWindows() {
        when(securityHelper.getCurrentHospitalId()).thenReturn(HOSPITAL_ID);
        TrainingCertification expired = new TrainingCertification();
        expired.setExpiresAt(LocalDate.now().minusDays(1));
        expired.setStatus("VALID");
        TrainingCertification expiring = new TrainingCertification();
        expiring.setExpiresAt(LocalDate.now().plusDays(10));
        expiring.setStatus("VALID");
        TrainingCertification valid = new TrainingCertification();
        valid.setExpiresAt(LocalDate.now().plusMonths(6));
        valid.setStatus("VALID");
        when(certificationRepository.findByHospitalIdOrderByIdDesc(HOSPITAL_ID)).thenReturn(List.of(expired, expiring, valid));
        when(certificationRepository.save(any(TrainingCertification.class))).thenAnswer(i -> i.getArgument(0));

        service.getCertifications();

        assertThat(expired.getStatus()).isEqualTo("EXPIRED");
        assertThat(expiring.getStatus()).isEqualTo("EXPIRING");
        assertThat(valid.getStatus()).isEqualTo("VALID");
    }
}
