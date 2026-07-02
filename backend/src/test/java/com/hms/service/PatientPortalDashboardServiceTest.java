package com.hms.service;

import com.hms.dto.PortalDashboardResponse;
import com.hms.dto.PortalReportResponse;
import com.hms.entity.*;
import com.hms.repository.*;
import com.hms.service.portal.PatientPortalDashboardService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PatientPortalDashboardServiceTest {

    @Mock private PatientPortalUserRepository portalUserRepository;
    @Mock private AppointmentRepository appointmentRepository;
    @Mock private LabOrderRepository labOrderRepository;
    @Mock private LabResultRepository labResultRepository;
    @Mock private RadiologyOrderRepository radiologyOrderRepository;
    @Mock private RadiologyResultRepository radiologyResultRepository;
    @Mock private MedicalRecordRepository medicalRecordRepository;
    @Mock private PrescriptionRepository prescriptionRepository;
    @Mock private BillingRepository billingRepository;
    @Mock private AuditLogRepository auditLogRepository;

    @InjectMocks
    private PatientPortalDashboardService service;

    private static final Long HOSPITAL_ID = 1L;
    private static final Long PORTAL_USER_ID = 5L;
    private static final Long PATIENT_ID = 1L;

    private PatientPortalUser portalUser() {
        PatientPortalUser u = new PatientPortalUser();
        u.setId(PORTAL_USER_ID);
        u.setHospitalId(HOSPITAL_ID);
        u.setPatientId(PATIENT_ID);
        u.setStatus("ACTIVE");
        return u;
    }

    // ===== BR-2: only RELEASED reports are ever returned =====

    @Test
    void getReports_excludesUnreleasedOrders() {
        when(portalUserRepository.findById(PORTAL_USER_ID)).thenReturn(Optional.of(portalUser()));

        LabOrder released = new LabOrder();
        released.setId(10L);
        released.setStatus("RELEASED");
        released.setTestName("CBC");
        LabOrder verified = new LabOrder();
        verified.setId(11L);
        verified.setStatus("VERIFIED");
        verified.setTestName("LFT");
        when(labOrderRepository.findByHospitalIdAndPatientIdOrderByCreatedAtDesc(HOSPITAL_ID, PATIENT_ID))
                .thenReturn(List.of(released, verified));
        when(radiologyOrderRepository.findByHospitalIdAndPatientIdOrderByCreatedAtDesc(HOSPITAL_ID, PATIENT_ID))
                .thenReturn(List.of());

        LabResult result = new LabResult();
        result.setResultSummary("Normal");
        result.setIsAbnormal(false);
        result.setReleasedAt(java.time.LocalDateTime.now());
        when(labResultRepository.findByLabOrderId(10L)).thenReturn(Optional.of(result));

        List<PortalReportResponse> reports = service.getReports(HOSPITAL_ID, PORTAL_USER_ID);

        assertThat(reports).hasSize(1);
        assertThat(reports.get(0).getOrderId()).isEqualTo(10L);
        assertThat(reports.get(0).getTestName()).isEqualTo("CBC");
    }

    // ===== dashboard summary =====

    @Test
    void getDashboard_summarizesCountsAndBalance() {
        when(portalUserRepository.findById(PORTAL_USER_ID)).thenReturn(Optional.of(portalUser()));
        Appointment upcoming = new Appointment();
        upcoming.setStatus("SCHEDULED");
        upcoming.setAppointmentDate(LocalDate.now().plusDays(2));
        when(appointmentRepository.findByPatientIdAndHospitalIdAndIsActiveTrueOrderByAppointmentDateDesc(PATIENT_ID, HOSPITAL_ID))
                .thenReturn(List.of(upcoming));

        LabOrder released = new LabOrder();
        released.setId(10L);
        released.setStatus("RELEASED");
        when(labOrderRepository.findByHospitalIdAndPatientIdOrderByCreatedAtDesc(HOSPITAL_ID, PATIENT_ID))
                .thenReturn(List.of(released));
        when(radiologyOrderRepository.findByHospitalIdAndPatientIdOrderByCreatedAtDesc(HOSPITAL_ID, PATIENT_ID))
                .thenReturn(List.of());

        when(medicalRecordRepository.findByPatientIdOrderByCreatedAtDesc(PATIENT_ID)).thenReturn(List.of());
        when(prescriptionRepository.findByMedicalRecordIdIn(List.of())).thenReturn(List.of());

        Billing pending = new Billing();
        pending.setAmount(new BigDecimal("1500"));
        pending.setPaymentStatus("PENDING");
        when(billingRepository.findByPatientIdOrderByCreatedAtDesc(PATIENT_ID)).thenReturn(List.of(pending));

        PortalDashboardResponse dashboard = service.getDashboard(HOSPITAL_ID, PORTAL_USER_ID);

        assertThat(dashboard.getUpcomingAppointments()).isEqualTo(1);
        assertThat(dashboard.getReleasedReports()).isEqualTo(1);
        assertThat(dashboard.getOutstandingBalance()).isEqualByComparingTo("1500");
    }

    // ===== tenant/patient scoping =====

    @Test
    void getAppointments_scopesToResolvedPatientId() {
        when(portalUserRepository.findById(PORTAL_USER_ID)).thenReturn(Optional.of(portalUser()));
        when(appointmentRepository.findByPatientIdAndHospitalIdAndIsActiveTrueOrderByAppointmentDateDesc(PATIENT_ID, HOSPITAL_ID))
                .thenReturn(List.of());

        service.getAppointments(HOSPITAL_ID, PORTAL_USER_ID);

        org.mockito.Mockito.verify(appointmentRepository)
                .findByPatientIdAndHospitalIdAndIsActiveTrueOrderByAppointmentDateDesc(PATIENT_ID, HOSPITAL_ID);
    }

    // ===== BR-4: every read is audit-logged =====

    @Test
    void getReports_writesAuditLogEntry() {
        when(portalUserRepository.findById(PORTAL_USER_ID)).thenReturn(Optional.of(portalUser()));
        when(labOrderRepository.findByHospitalIdAndPatientIdOrderByCreatedAtDesc(HOSPITAL_ID, PATIENT_ID))
                .thenReturn(List.of());
        when(radiologyOrderRepository.findByHospitalIdAndPatientIdOrderByCreatedAtDesc(HOSPITAL_ID, PATIENT_ID))
                .thenReturn(List.of());

        service.getReports(HOSPITAL_ID, PORTAL_USER_ID);

        org.mockito.Mockito.verify(auditLogRepository).save(org.mockito.ArgumentMatchers.argThat(
                entry -> entry.getAction().equals("PATIENT_PORTAL_REPORTS_ACCESSED")));
    }

    @Test
    void getDashboard_countsOnlyActivePrescriptions() {
        when(portalUserRepository.findById(PORTAL_USER_ID)).thenReturn(Optional.of(portalUser()));
        when(appointmentRepository.findByPatientIdAndHospitalIdAndIsActiveTrueOrderByAppointmentDateDesc(PATIENT_ID, HOSPITAL_ID))
                .thenReturn(List.of());
        when(labOrderRepository.findByHospitalIdAndPatientIdOrderByCreatedAtDesc(HOSPITAL_ID, PATIENT_ID))
                .thenReturn(List.of());
        when(radiologyOrderRepository.findByHospitalIdAndPatientIdOrderByCreatedAtDesc(HOSPITAL_ID, PATIENT_ID))
                .thenReturn(List.of());

        MedicalRecord record = new MedicalRecord();
        record.setId(20L);
        when(medicalRecordRepository.findByPatientIdOrderByCreatedAtDesc(PATIENT_ID)).thenReturn(List.of(record));

        Prescription active = new Prescription();
        active.setStatus("ACTIVE");
        Prescription stopped = new Prescription();
        stopped.setStatus("STOPPED");
        when(prescriptionRepository.findByMedicalRecordIdIn(List.of(20L))).thenReturn(List.of(active, stopped));

        when(billingRepository.findByPatientIdOrderByCreatedAtDesc(PATIENT_ID)).thenReturn(List.of());

        PortalDashboardResponse dashboard = service.getDashboard(HOSPITAL_ID, PORTAL_USER_ID);

        assertThat(dashboard.getActivePrescriptions()).isEqualTo(1);
    }

    @Test
    void getAppointments_rejectedWhenPortalAccountNotActive() {
        PatientPortalUser suspended = portalUser();
        suspended.setStatus("SUSPENDED");
        when(portalUserRepository.findById(PORTAL_USER_ID)).thenReturn(Optional.of(suspended));

        assertThatThrownBy(() -> service.getAppointments(HOSPITAL_ID, PORTAL_USER_ID))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not active");
    }
}
