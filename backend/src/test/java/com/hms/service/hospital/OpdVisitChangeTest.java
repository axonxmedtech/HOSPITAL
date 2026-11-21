package com.hms.service.hospital;

import com.hms.entity.Doctor;
import com.hms.entity.Opd;
import com.hms.entity.Patient;
import com.hms.entity.QueueEntry;
import com.hms.repository.DoctorRepository;
import com.hms.repository.MedicalRecordRepository;
import com.hms.repository.OpdRepository;
import com.hms.repository.PatientRepository;
import com.hms.repository.QueueEntryRepository;
import com.hms.repository.UserRepository;
import com.hms.security.HospitalWebSocketHandler;
import com.hms.security.SecurityContextHelper;
import com.hms.service.AuditLogService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * A waiting OPD visit can be cancelled or moved to another doctor. Before this, a patient who
 * left without being seen stayed in the doctor's queue for ever.
 */
@ExtendWith(MockitoExtension.class)
class OpdVisitChangeTest {

    @Mock OpdRepository opdRepository;
    @Mock com.hms.repository.IpdAdmissionRepository ipdAdmissionRepository;
    @Mock QueueEntryRepository queueEntryRepository;
    @Mock PatientRepository patientRepository;
    @Mock DoctorRepository doctorRepository;
    @Mock UserRepository userRepository;
    @Mock MedicalRecordRepository medicalRecordRepository;
    @Mock SecurityContextHelper securityHelper;
    @Mock AuditLogService auditLogService;
    @Mock HospitalWebSocketHandler webSocketHandler;
    @Mock VitalSettingsService vitalSettingsService;
    @Mock BillingService billingService;
    @Mock HospitalStaffing staffing;

    OpdService service;
    Opd visit;
    Doctor asha;
    Doctor ravi;

    private static Doctor doctor(long id, String name, String email) {
        Doctor d = new Doctor();
        d.setId(id);
        d.setName(name);
        d.setEmail(email);
        d.setHospitalId(1L);
        d.setIsActive(true);
        return d;
    }

    @BeforeEach
    void setUp() {
        service = new OpdService(opdRepository, queueEntryRepository, patientRepository, doctorRepository,
                userRepository, medicalRecordRepository, securityHelper, auditLogService, webSocketHandler,
                vitalSettingsService, staffing, ipdAdmissionRepository);
        ReflectionTestUtils.setField(service, "billingService", billingService);
        asha = doctor(10L, "Dr Asha", "asha@h.test");
        ravi = doctor(11L, "Ravi", "ravi@h.test");
        visit = new Opd();
        visit.setId(5L);
        visit.setCaseId("OPD-5");
        visit.setPatient(new Patient());
        visit.setDoctor(asha);
        lenient().when(securityHelper.getCurrentHospitalId()).thenReturn(1L);
        lenient().when(opdRepository.findByIdAndHospitalIdWithPatientAndDoctorForUpdate(5L, 1L))
                .thenReturn(Optional.of(visit));
        lenient().when(staffing.of(1L)).thenReturn(new HospitalStaffing.Snapshot(true, false, true, true, true));
    }

    private void actingAs(String role, String email) {
        lenient().when(securityHelper.getCurrentUserRole()).thenReturn(role);
        lenient().when(securityHelper.getCurrentUserEmail()).thenReturn(email);
    }

    @Test
    void receptionCancelsAWaitingVisitAndItLeavesTheQueue() {
        actingAs("RECEPTIONIST", "desk@h.test");
        when(billingService.settleForCancelledOpd(5L, "Left without being seen", null)).thenReturn("Bill BIL1 cancelled.");

        Map<String, Object> out = service.cancelVisit(5L, "  Left without being seen ", null);

        assertThat(visit.getStatus()).isEqualTo(Opd.Status.CANCELLED);
        assertThat(visit.getCancelReason()).isEqualTo("Left without being seen");
        assertThat(visit.getCancelledAt()).isNotNull();
        verify(queueEntryRepository).deleteByOpdId(5L);
        assertThat(out.get("message")).asString().contains("OPD-5 cancelled").contains("BIL1");
    }

    @Test
    void aReasonIsRequired() {
        actingAs("RECEPTIONIST", "desk@h.test");
        assertThatThrownBy(() -> service.cancelVisit(5L, " ", null)).hasMessageContaining("reason");
        verify(queueEntryRepository, never()).deleteByOpdId(anyLong());
    }

    @Test
    void aVisitTheDoctorHasSeenCannotBeCancelled() {
        actingAs("HOSPITAL_ADMIN", "admin@h.test");
        visit.setStatus(Opd.Status.COMPLETED);
        assertThatThrownBy(() -> service.cancelVisit(5L, "x", null)).hasMessageContaining("already seen");
        visit.setStatus(Opd.Status.IN_IPD);
        assertThatThrownBy(() -> service.changeDoctor(5L, "11")).hasMessageContaining("admitted");
    }

    @Test
    void aDoctorMayCancelTheirOwnPatientButNotAnotherDoctors() {
        actingAs("DOCTOR", "ravi@h.test");
        when(doctorRepository.findByEmailAndHospitalId("ravi@h.test", 1L)).thenReturn(Optional.of(ravi));
        assertThatThrownBy(() -> service.cancelVisit(5L, "x", null))
                .isInstanceOf(AccessDeniedException.class).hasMessageContaining("another doctor's queue");

        actingAs("DOCTOR", "asha@h.test");
        when(doctorRepository.findByEmailAndHospitalId("asha@h.test", 1L)).thenReturn(Optional.of(asha));
        service.cancelVisit(5L, "Patient asked to cancel", null);
        assertThat(visit.getStatus()).isEqualTo(Opd.Status.CANCELLED);
    }

    @Test
    void aDoctorRunningTheDeskMayCancelAnyVisit() {
        actingAs("DOCTOR", "ravi@h.test");
        when(staffing.of(1L)).thenReturn(new HospitalStaffing.Snapshot(false, true, false, false, false));
        service.cancelVisit(5L, "Duplicate", null);
        assertThat(visit.getStatus()).isEqualTo(Opd.Status.CANCELLED);
    }

    @Test
    void changingTheDoctorRequeuesThePatientInTheirOriginalPlace() {
        actingAs("RECEPTIONIST", "desk@h.test");
        when(doctorRepository.findByIdAndHospitalIdAndIsActiveTrue(11L, 1L)).thenReturn(Optional.of(ravi));
        when(opdRepository.save(any(Opd.class))).thenAnswer(i -> i.getArgument(0));
        QueueEntry old = new QueueEntry();
        LocalDateTime queuedAt = LocalDateTime.now().minusMinutes(40);
        old.setCreatedAt(queuedAt);
        when(queueEntryRepository.findByOpd_Id(5L)).thenReturn(List.of(old));

        service.changeDoctor(5L, "11");

        assertThat(visit.getDoctor()).isSameAs(ravi);
        ArgumentCaptor<QueueEntry> saved = ArgumentCaptor.forClass(QueueEntry.class);
        verify(queueEntryRepository).deleteByOpdId(5L);
        verify(queueEntryRepository).save(saved.capture());
        assertThat(saved.getValue().getDoctor()).isSameAs(ravi);
        assertThat(saved.getValue().getCreatedAt()).isEqualTo(queuedAt);
        verify(billingService).moveOpdBillToDoctor(5L, 11L);
    }

    @Test
    void changingToTheSameOrAnUnknownDoctorIsExplained() {
        actingAs("RECEPTIONIST", "desk@h.test");
        when(doctorRepository.findByIdAndHospitalIdAndIsActiveTrue(10L, 1L)).thenReturn(Optional.of(asha));
        assertThatThrownBy(() -> service.changeDoctor(5L, "10")).hasMessageContaining("already in Dr Asha's queue");
        when(doctorRepository.findByIdAndHospitalIdAndIsActiveTrue(eq(99L), eq(1L))).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.changeDoctor(5L, "99")).hasMessageContaining("not available");
    }
}
