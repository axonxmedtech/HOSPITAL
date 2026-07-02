package com.hms.service;

import com.hms.dto.*;
import com.hms.entity.Bed;
import com.hms.entity.ExecutiveAlert;
import com.hms.repository.*;
import com.hms.repository.pharmacy.MedicineBatchRepository;
import com.hms.security.SecurityContextHelper;
import com.hms.service.hospital.AdminDashboardService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminDashboardServiceTest {

    @Mock private BedRepository bedRepository;
    @Mock private BillingRepository billingRepository;
    @Mock private OtBookingRepository otBookingRepository;
    @Mock private MedicineBatchRepository medicineBatchRepository;
    @Mock private ExecutiveAlertRepository alertRepository;
    @Mock private SecurityContextHelper securityHelper;
    @Mock private AuditLogRepository auditLogRepository;

    @InjectMocks
    private AdminDashboardService service;

    private static final Long HOSPITAL_ID = 1L;

    private Bed bed(String status) {
        Bed b = new Bed();
        b.setStatus(status);
        return b;
    }

    // ===== bed occupancy computation =====

    @Test
    void getExecutiveDashboard_computesOccupancyRate() {
        when(securityHelper.getCurrentHospitalId()).thenReturn(HOSPITAL_ID);
        when(bedRepository.findByHospitalId(HOSPITAL_ID)).thenReturn(List.of(bed("occupied"), bed("occupied"), bed("available"), bed("available")));
        when(billingRepository.sumAmountByHospitalIdAndPaymentStatusSince(eq(HOSPITAL_ID), eq("PAID"), any())).thenReturn(new BigDecimal("50000"));
        when(billingRepository.sumAmountByHospitalIdAndPaymentStatusSince(eq(HOSPITAL_ID), eq("PENDING"), any())).thenReturn(new BigDecimal("5000"));
        Page<Object> emptyPage = new PageImpl<>(List.of());
        when(medicineBatchRepository.findExpiringSoon(any(), any(), any(Pageable.class))).thenReturn((Page) emptyPage);
        when(alertRepository.countByHospitalIdAndStatus(HOSPITAL_ID, "ACTIVE")).thenReturn(3L);
        when(alertRepository.countByHospitalIdAndStatusAndSeverity(HOSPITAL_ID, "ACTIVE", "CRITICAL")).thenReturn(1L);

        ExecutiveDashboardResponse resp = service.getExecutiveDashboard("TODAY");

        assertThat(resp.getTotalBeds()).isEqualTo(4);
        assertThat(resp.getOccupiedBeds()).isEqualTo(2);
        assertThat(resp.getBedOccupancyRate()).isEqualByComparingTo("50.00");
        assertThat(resp.getTotalRevenue()).isEqualByComparingTo("50000");
        assertThat(resp.getActiveAlerts()).isEqualTo(3);
    }

    @Test
    void getExecutiveDashboard_rejectedForInvalidTimeframe() {
        when(securityHelper.getCurrentHospitalId()).thenReturn(HOSPITAL_ID);

        assertThatThrownBy(() -> service.getExecutiveDashboard("DAILY"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Timeframe");
    }

    @Test
    void getExecutiveDashboard_zeroBedsYieldsZeroRate() {
        when(securityHelper.getCurrentHospitalId()).thenReturn(HOSPITAL_ID);
        when(bedRepository.findByHospitalId(HOSPITAL_ID)).thenReturn(List.of());
        when(billingRepository.sumAmountByHospitalIdAndPaymentStatusSince(any(), any(), any())).thenReturn(null);
        Page<Object> emptyPage = new PageImpl<>(List.of());
        when(medicineBatchRepository.findExpiringSoon(any(), any(), any(Pageable.class))).thenReturn((Page) emptyPage);

        ExecutiveDashboardResponse resp = service.getExecutiveDashboard(null);

        assertThat(resp.getBedOccupancyRate()).isEqualByComparingTo("0");
        assertThat(resp.getTotalRevenue()).isEqualByComparingTo("0");
    }

    // ===== executive alert lifecycle =====

    @Test
    void createAlert_rejectedForInvalidSeverity() {
        when(securityHelper.getCurrentHospitalId()).thenReturn(HOSPITAL_ID);
        ExecutiveAlertRequest req = new ExecutiveAlertRequest();
        req.setSeverity("URGENT");
        req.setTitle("Test");
        req.setDescription("Test desc");

        assertThatThrownBy(() -> service.createAlert(req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Severity");
        verify(alertRepository, never()).save(any());
    }

    @Test
    void createAlert_savesValidAlert() {
        when(securityHelper.getCurrentHospitalId()).thenReturn(HOSPITAL_ID);
        when(securityHelper.getCurrentUserEmail()).thenReturn("admin@hospital.com");
        when(alertRepository.save(any(ExecutiveAlert.class))).thenAnswer(i -> i.getArgument(0));

        ExecutiveAlertRequest req = new ExecutiveAlertRequest();
        req.setSeverity("critical");
        req.setTitle("ICU occupancy > 95%");
        req.setDescription("ICU bed census breached the safety threshold");

        ExecutiveAlert saved = service.createAlert(req);

        assertThat(saved.getSeverity()).isEqualTo("CRITICAL");
        assertThat(saved.getStatus()).isEqualTo("ACTIVE");
    }

    // ===== BR-6: resolved alerts are immutable =====

    @Test
    void acknowledgeAlert_rejectedWhenAlreadyResolved() {
        when(securityHelper.getCurrentHospitalId()).thenReturn(HOSPITAL_ID);
        ExecutiveAlert alert = new ExecutiveAlert();
        alert.setId(9L);
        alert.setHospitalId(HOSPITAL_ID);
        alert.setStatus("RESOLVED");
        when(alertRepository.findByIdAndHospitalId(9L, HOSPITAL_ID)).thenReturn(Optional.of(alert));

        AlertAcknowledgeRequest req = new AlertAcknowledgeRequest();
        req.setAlertId(9L);
        req.setStatus("ACKNOWLEDGED");
        req.setRemarks("Checked");

        assertThatThrownBy(() -> service.acknowledgeAlert(req))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("BR-6");
    }

    @Test
    void acknowledgeAlert_requiresRemarks() {
        when(securityHelper.getCurrentHospitalId()).thenReturn(HOSPITAL_ID);
        ExecutiveAlert alert = new ExecutiveAlert();
        alert.setId(9L);
        alert.setHospitalId(HOSPITAL_ID);
        alert.setStatus("ACTIVE");
        when(alertRepository.findByIdAndHospitalId(9L, HOSPITAL_ID)).thenReturn(Optional.of(alert));

        AlertAcknowledgeRequest req = new AlertAcknowledgeRequest();
        req.setAlertId(9L);
        req.setStatus("ACKNOWLEDGED");

        assertThatThrownBy(() -> service.acknowledgeAlert(req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("remark");
        verify(alertRepository, never()).save(any());
    }

    @Test
    void acknowledgeAlert_resolvedSetsResolvedAt() {
        when(securityHelper.getCurrentHospitalId()).thenReturn(HOSPITAL_ID);
        when(securityHelper.getCurrentUserEmail()).thenReturn("admin@hospital.com");
        ExecutiveAlert alert = new ExecutiveAlert();
        alert.setId(9L);
        alert.setHospitalId(HOSPITAL_ID);
        alert.setStatus("ACTIVE");
        when(alertRepository.findByIdAndHospitalId(9L, HOSPITAL_ID)).thenReturn(Optional.of(alert));
        when(alertRepository.save(any(ExecutiveAlert.class))).thenAnswer(i -> i.getArgument(0));

        AlertAcknowledgeRequest req = new AlertAcknowledgeRequest();
        req.setAlertId(9L);
        req.setStatus("RESOLVED");
        req.setRemarks("Biomedical engineer dispatched");

        ExecutiveAlert saved = service.acknowledgeAlert(req);

        assertThat(saved.getStatus()).isEqualTo("RESOLVED");
        assertThat(saved.getResolvedAt()).isNotNull();
    }
}
