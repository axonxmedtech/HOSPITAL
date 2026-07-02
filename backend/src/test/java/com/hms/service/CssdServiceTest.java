package com.hms.service;

import com.hms.dto.*;
import com.hms.entity.CssdIssue;
import com.hms.entity.CssdTray;
import com.hms.entity.SterilizationCycle;
import com.hms.repository.AuditLogRepository;
import com.hms.repository.CssdIssueRepository;
import com.hms.repository.CssdTrayRepository;
import com.hms.repository.SterilizationCycleRepository;
import com.hms.security.SecurityContextHelper;
import com.hms.service.hospital.CssdService;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CssdServiceTest {

    @Mock private CssdTrayRepository trayRepository;
    @Mock private SterilizationCycleRepository cycleRepository;
    @Mock private CssdIssueRepository issueRepository;
    @Mock private SecurityContextHelper securityHelper;
    @Mock private AuditLogRepository auditLogRepository;

    @InjectMocks
    private CssdService service;

    private static final Long HOSPITAL_ID = 1L;

    private CssdTray tray(String barcode, String status) {
        CssdTray t = new CssdTray();
        t.setId(10L);
        t.setHospitalId(HOSPITAL_ID);
        t.setTrayName("Major Laparotomy Set");
        t.setBarcode(barcode);
        t.setStatus(status);
        return t;
    }

    // ===== BR-1: sterility lock =====

    @Test
    void issueTray_rejectedWhenNotSterile() {
        when(securityHelper.getCurrentHospitalId()).thenReturn(HOSPITAL_ID);
        CssdTray tray = tray("TRAY-1", "DIRTY");
        when(trayRepository.findByHospitalIdAndBarcode(HOSPITAL_ID, "TRAY-1")).thenReturn(Optional.of(tray));

        CssdIssueRequest req = new CssdIssueRequest();
        req.setTrayBarcode("TRAY-1");
        req.setIssuedToDepartment("OT-2");

        assertThatThrownBy(() -> service.issueTray(req))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not sterile");
        verify(issueRepository, never()).save(any());
    }

    @Test
    void issueTray_succeedsWhenSterile() {
        when(securityHelper.getCurrentHospitalId()).thenReturn(HOSPITAL_ID);
        when(securityHelper.getCurrentUserEmail()).thenReturn("nurse@hospital.com");
        CssdTray tray = tray("TRAY-1", "STERILE");
        tray.setExpiryDate(LocalDate.now().plusDays(10));
        when(trayRepository.findByHospitalIdAndBarcode(HOSPITAL_ID, "TRAY-1")).thenReturn(Optional.of(tray));
        when(issueRepository.save(any(CssdIssue.class))).thenAnswer(i -> i.getArgument(0));

        CssdIssueRequest req = new CssdIssueRequest();
        req.setTrayBarcode("TRAY-1");
        req.setIssuedToDepartment("OT-2");
        req.setReceivedBy(8L);

        CssdIssue saved = service.issueTray(req);

        assertThat(saved.getIssuedToDepartment()).isEqualTo("OT-2");
        assertThat(tray.getStatus()).isEqualTo("ISSUED");
        verify(trayRepository).save(tray);
    }

    // ===== BR-3: expiry prevention =====

    @Test
    void issueTray_expiredTray_blockedAndRoutedBackToCleaning() {
        when(securityHelper.getCurrentHospitalId()).thenReturn(HOSPITAL_ID);
        CssdTray tray = tray("TRAY-1", "STERILE");
        tray.setExpiryDate(LocalDate.now().minusDays(1));
        when(trayRepository.findByHospitalIdAndBarcode(HOSPITAL_ID, "TRAY-1")).thenReturn(Optional.of(tray));

        CssdIssueRequest req = new CssdIssueRequest();
        req.setTrayBarcode("TRAY-1");
        req.setIssuedToDepartment("OT-2");

        assertThatThrownBy(() -> service.issueTray(req))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("expiry");
        assertThat(tray.getStatus()).isEqualTo("DIRTY");
        verify(issueRepository, never()).save(any());
    }

    // ===== BR-2: fail quarantine =====

    @Test
    void verifyCycle_biologicalFail_quarantinesAllTraysInLoad() {
        when(securityHelper.getCurrentHospitalId()).thenReturn(HOSPITAL_ID);
        when(securityHelper.getCurrentUserId()).thenReturn(99L);
        SterilizationCycle cycle = new SterilizationCycle();
        cycle.setId(5L);
        cycle.setHospitalId(HOSPITAL_ID);
        cycle.setCycleNumber("STER-01-1");
        cycle.setStatus("IN_PROGRESS");
        when(cycleRepository.findByIdAndHospitalId(5L, HOSPITAL_ID)).thenReturn(Optional.of(cycle));
        when(cycleRepository.save(any(SterilizationCycle.class))).thenAnswer(i -> i.getArgument(0));

        CssdTray t1 = tray("TRAY-1", "IN_STERILIZER");
        CssdTray t2 = tray("TRAY-2", "IN_STERILIZER");
        when(trayRepository.findByHospitalIdAndCycleId(HOSPITAL_ID, 5L)).thenReturn(List.of(t1, t2));

        CssdCycleVerifyRequest req = new CssdCycleVerifyRequest();
        req.setChemicalResult("PASS");
        req.setBiologicalResult("FAIL");
        req.setApprovedBySig("sig-data");

        SterilizationCycle saved = service.verifyCycle(5L, req);

        assertThat(saved.getStatus()).isEqualTo("FAILED");
        assertThat(t1.getStatus()).isEqualTo("QUARANTINED");
        assertThat(t2.getStatus()).isEqualTo("QUARANTINED");
        verify(trayRepository, times(2)).save(any(CssdTray.class));
    }

    @Test
    void verifyCycle_bothPass_releasesTraysSterileWithExpiry() {
        when(securityHelper.getCurrentHospitalId()).thenReturn(HOSPITAL_ID);
        when(securityHelper.getCurrentUserId()).thenReturn(99L);
        SterilizationCycle cycle = new SterilizationCycle();
        cycle.setId(5L);
        cycle.setHospitalId(HOSPITAL_ID);
        cycle.setCycleNumber("STER-01-1");
        cycle.setStatus("IN_PROGRESS");
        when(cycleRepository.findByIdAndHospitalId(5L, HOSPITAL_ID)).thenReturn(Optional.of(cycle));
        when(cycleRepository.save(any(SterilizationCycle.class))).thenAnswer(i -> i.getArgument(0));

        CssdTray t1 = tray("TRAY-1", "IN_STERILIZER");
        when(trayRepository.findByHospitalIdAndCycleId(HOSPITAL_ID, 5L)).thenReturn(List.of(t1));

        CssdCycleVerifyRequest req = new CssdCycleVerifyRequest();
        req.setChemicalResult("PASS");
        req.setBiologicalResult("PASS");

        SterilizationCycle saved = service.verifyCycle(5L, req);

        assertThat(saved.getStatus()).isEqualTo("PASSED");
        assertThat(t1.getStatus()).isEqualTo("STERILE");
        assertThat(t1.getExpiryDate()).isAfter(LocalDate.now());
    }

    @Test
    void verifyCycle_rejectedWhenAlreadyVerified() {
        when(securityHelper.getCurrentHospitalId()).thenReturn(HOSPITAL_ID);
        SterilizationCycle cycle = new SterilizationCycle();
        cycle.setId(5L);
        cycle.setHospitalId(HOSPITAL_ID);
        cycle.setStatus("PASSED");
        when(cycleRepository.findByIdAndHospitalId(5L, HOSPITAL_ID)).thenReturn(Optional.of(cycle));

        CssdCycleVerifyRequest req = new CssdCycleVerifyRequest();
        req.setChemicalResult("PASS");
        req.setBiologicalResult("PASS");

        assertThatThrownBy(() -> service.verifyCycle(5L, req))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("already been verified");
    }

    // ===== startCycle preconditions =====

    @Test
    void startCycle_rejectedWhenTrayNotDirty() {
        when(securityHelper.getCurrentHospitalId()).thenReturn(HOSPITAL_ID);
        CssdTray tray = tray("TRAY-1", "STERILE");
        when(trayRepository.findByIdAndHospitalId(10L, HOSPITAL_ID)).thenReturn(Optional.of(tray));

        CssdCycleStartRequest req = new CssdCycleStartRequest();
        req.setMachineId("STER-01");
        req.setMethod("STEAM");
        req.setTemperature(new BigDecimal("134.0"));
        req.setPressure(new BigDecimal("2.10"));
        req.setDuration(20);
        req.setTrayIds(List.of(10L));

        assertThatThrownBy(() -> service.startCycle(req))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("cannot be loaded");
        verify(cycleRepository, never()).save(any());
    }

    @Test
    void startCycle_movesDirtyTraysIntoSterilizer() {
        when(securityHelper.getCurrentHospitalId()).thenReturn(HOSPITAL_ID);
        CssdTray tray = tray("TRAY-1", "DIRTY");
        when(trayRepository.findByIdAndHospitalId(10L, HOSPITAL_ID)).thenReturn(Optional.of(tray));
        when(cycleRepository.countByHospitalId(HOSPITAL_ID)).thenReturn(0L);
        when(cycleRepository.save(any(SterilizationCycle.class))).thenAnswer(i -> {
            SterilizationCycle c = i.getArgument(0);
            c.setId(5L);
            return c;
        });

        CssdCycleStartRequest req = new CssdCycleStartRequest();
        req.setMachineId("STER-01");
        req.setMethod("steam");
        req.setTemperature(new BigDecimal("134.0"));
        req.setPressure(new BigDecimal("2.10"));
        req.setDuration(20);
        req.setTrayIds(List.of(10L));

        SterilizationCycle saved = service.startCycle(req);

        assertThat(saved.getMethod()).isEqualTo("STEAM");
        assertThat(tray.getStatus()).isEqualTo("IN_STERILIZER");
        assertThat(tray.getCycleId()).isEqualTo(5L);
    }

    // ===== BR-7: tenant isolation on lookups =====

    @Test
    void issueTray_trayFromAnotherHospital_notFound() {
        when(securityHelper.getCurrentHospitalId()).thenReturn(HOSPITAL_ID);
        when(trayRepository.findByHospitalIdAndBarcode(HOSPITAL_ID, "TRAY-X")).thenReturn(Optional.empty());

        CssdIssueRequest req = new CssdIssueRequest();
        req.setTrayBarcode("TRAY-X");
        req.setIssuedToDepartment("OT-2");

        assertThatThrownBy(() -> service.issueTray(req))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Tray not found");
    }

    // ===== register / return =====

    @Test
    void registerTray_rejectedWhenBarcodeAlreadyExists() {
        when(securityHelper.getCurrentHospitalId()).thenReturn(HOSPITAL_ID);
        when(trayRepository.findByHospitalIdAndBarcode(HOSPITAL_ID, "TRAY-1")).thenReturn(Optional.of(tray("TRAY-1", "DIRTY")));

        CssdTrayRegisterRequest req = new CssdTrayRegisterRequest();
        req.setTrayName("Major Laparotomy Set");
        req.setBarcode("TRAY-1");

        assertThatThrownBy(() -> service.registerTray(req))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("already exists");
    }

    @Test
    void returnTray_damagedCondition_logsDiscrepancyAudit() {
        when(securityHelper.getCurrentHospitalId()).thenReturn(HOSPITAL_ID);
        CssdTray t = tray("TRAY-1", "ISSUED");
        when(trayRepository.findByHospitalIdAndBarcode(HOSPITAL_ID, "TRAY-1")).thenReturn(Optional.of(t));
        when(trayRepository.save(any(CssdTray.class))).thenAnswer(i -> i.getArgument(0));

        CssdReturnRequest req = new CssdReturnRequest();
        req.setTrayBarcode("TRAY-1");
        req.setFromDepartment("OT-2");
        req.setCondition("DAMAGED");

        CssdTray saved = service.returnTray(req);

        assertThat(saved.getStatus()).isEqualTo("DIRTY");
        verify(auditLogRepository).save(argThat(entry -> entry.getAction().equals("CSSD_RETURN_DISCREPANCY")));
    }
}
