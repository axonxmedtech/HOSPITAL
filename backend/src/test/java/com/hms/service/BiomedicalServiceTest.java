package com.hms.service;

import com.hms.dto.*;
import com.hms.entity.BreakdownTicket;
import com.hms.entity.CalibrationRecord;
import com.hms.entity.MedicalEquipment;
import com.hms.repository.AuditLogRepository;
import com.hms.repository.BreakdownTicketRepository;
import com.hms.repository.CalibrationRecordRepository;
import com.hms.repository.MedicalEquipmentRepository;
import com.hms.security.SecurityContextHelper;
import com.hms.service.hospital.BiomedicalService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BiomedicalServiceTest {

    @Mock private MedicalEquipmentRepository equipmentRepository;
    @Mock private BreakdownTicketRepository ticketRepository;
    @Mock private CalibrationRecordRepository calibrationRepository;
    @Mock private SecurityContextHelper securityHelper;
    @Mock private AuditLogRepository auditLogRepository;

    @InjectMocks
    private BiomedicalService service;

    private static final Long HOSPITAL_ID = 1L;

    private MedicalEquipment equipment(String status) {
        MedicalEquipment e = new MedicalEquipment();
        e.setId(20L);
        e.setHospitalId(HOSPITAL_ID);
        e.setAssetCode("EQ-1-1");
        e.setEquipmentName("Ventilator V60");
        e.setCategory("ICU");
        e.setSerialNumber("SN-776");
        e.setDepartment("ICU");
        e.setStatus(status);
        return e;
    }

    // ===== BR-1: unique asset code =====

    @Test
    void registerEquipment_generatesSequentialAssetCode() {
        when(securityHelper.getCurrentHospitalId()).thenReturn(HOSPITAL_ID);
        when(equipmentRepository.countByHospitalId(HOSPITAL_ID)).thenReturn(3L);
        when(equipmentRepository.save(any(MedicalEquipment.class))).thenAnswer(i -> i.getArgument(0));

        EquipmentRegisterRequest req = new EquipmentRegisterRequest();
        req.setEquipmentName("Ventilator V60");
        req.setCategory("ICU");
        req.setSerialNumber("SN-776");
        req.setDepartment("ICU");

        MedicalEquipment saved = service.registerEquipment(req);

        assertThat(saved.getAssetCode()).isEqualTo("EQ-1-4");
        assertThat(saved.getStatus()).isEqualTo("ACTIVE");
    }

    @Test
    void registerEquipment_rejectedWhenSerialNumberMissing() {
        when(securityHelper.getCurrentHospitalId()).thenReturn(HOSPITAL_ID);
        EquipmentRegisterRequest req = new EquipmentRegisterRequest();
        req.setEquipmentName("Ventilator V60");
        req.setCategory("ICU");
        req.setDepartment("ICU");

        assertThatThrownBy(() -> service.registerEquipment(req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Serial number");
        verify(equipmentRepository, never()).save(any());
    }

    // ===== Breakdown ticket flags device DOWN =====

    @Test
    void openBreakdownTicket_flagsDeviceDown() {
        when(securityHelper.getCurrentHospitalId()).thenReturn(HOSPITAL_ID);
        when(securityHelper.getCurrentUserEmail()).thenReturn("nurse@hospital.com");
        MedicalEquipment eq = equipment("ACTIVE");
        when(equipmentRepository.findByIdAndHospitalId(20L, HOSPITAL_ID)).thenReturn(Optional.of(eq));
        when(ticketRepository.save(any(BreakdownTicket.class))).thenAnswer(i -> i.getArgument(0));

        BreakdownTicketRequest req = new BreakdownTicketRequest();
        req.setEquipmentId(20L);
        req.setPriority("CRITICAL");
        req.setRemarks("Screen flickering and alarms failing");

        BreakdownTicket saved = service.openBreakdownTicket(req);

        assertThat(saved.getStatus()).isEqualTo("OPEN");
        assertThat(eq.getStatus()).isEqualTo("DOWN");
        verify(equipmentRepository).save(eq);
    }

    @Test
    void openBreakdownTicket_rejectedWhenRemarksBlank() {
        when(securityHelper.getCurrentHospitalId()).thenReturn(HOSPITAL_ID);
        when(equipmentRepository.findByIdAndHospitalId(20L, HOSPITAL_ID)).thenReturn(Optional.of(equipment("ACTIVE")));

        BreakdownTicketRequest req = new BreakdownTicketRequest();
        req.setEquipmentId(20L);
        req.setPriority("LOW");
        req.setRemarks("  ");

        assertThatThrownBy(() -> service.openBreakdownTicket(req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Problem description");
    }

    @Test
    void openBreakdownTicket_rejectedForRetiredEquipment() {
        when(securityHelper.getCurrentHospitalId()).thenReturn(HOSPITAL_ID);
        when(equipmentRepository.findByIdAndHospitalId(20L, HOSPITAL_ID)).thenReturn(Optional.of(equipment("RETIRED")));

        BreakdownTicketRequest req = new BreakdownTicketRequest();
        req.setEquipmentId(20L);
        req.setPriority("LOW");
        req.setRemarks("Won't power on");

        assertThatThrownBy(() -> service.openBreakdownTicket(req))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("retired");
    }

    // ===== BR-2: calibration PASS clears device, FAIL keeps it down =====

    @Test
    void recordCalibration_pass_restoresEquipmentToActive() {
        when(securityHelper.getCurrentHospitalId()).thenReturn(HOSPITAL_ID);
        MedicalEquipment eq = equipment("DOWN");
        when(equipmentRepository.findByIdAndHospitalId(20L, HOSPITAL_ID)).thenReturn(Optional.of(eq));
        when(calibrationRepository.save(any(CalibrationRecord.class))).thenAnswer(i -> i.getArgument(0));

        CalibrationRequest req = new CalibrationRequest();
        req.setEquipmentId(20L);
        req.setCalibrationDate(LocalDate.now());
        req.setDueDate(LocalDate.now().plusYears(1));
        req.setAgency("TUV India");
        req.setResult("PASS");

        service.recordCalibration(req);

        assertThat(eq.getStatus()).isEqualTo("ACTIVE");
    }

    @Test
    void recordCalibration_fail_keepsEquipmentDown() {
        when(securityHelper.getCurrentHospitalId()).thenReturn(HOSPITAL_ID);
        MedicalEquipment eq = equipment("ACTIVE");
        when(equipmentRepository.findByIdAndHospitalId(20L, HOSPITAL_ID)).thenReturn(Optional.of(eq));
        when(calibrationRepository.save(any(CalibrationRecord.class))).thenAnswer(i -> i.getArgument(0));

        CalibrationRequest req = new CalibrationRequest();
        req.setEquipmentId(20L);
        req.setCalibrationDate(LocalDate.now());
        req.setDueDate(LocalDate.now().plusYears(1));
        req.setAgency("TUV India");
        req.setResult("FAIL");

        service.recordCalibration(req);

        assertThat(eq.getStatus()).isEqualTo("DOWN");
    }

    @Test
    void recordCalibration_rejectedWhenDueDateNotAfterCalibrationDate() {
        when(securityHelper.getCurrentHospitalId()).thenReturn(HOSPITAL_ID);
        when(equipmentRepository.findByIdAndHospitalId(20L, HOSPITAL_ID)).thenReturn(Optional.of(equipment("ACTIVE")));

        CalibrationRequest req = new CalibrationRequest();
        req.setEquipmentId(20L);
        req.setCalibrationDate(LocalDate.now());
        req.setDueDate(LocalDate.now());
        req.setAgency("TUV India");
        req.setResult("PASS");

        assertThatThrownBy(() -> service.recordCalibration(req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Due date must be after");
        verify(calibrationRepository, never()).save(any());
    }

    // ===== BR-2: overdue sweep on read =====

    @Test
    void getEquipment_flagsCalibrationOverdueWhenDueDatePassed() {
        when(securityHelper.getCurrentHospitalId()).thenReturn(HOSPITAL_ID);
        MedicalEquipment eq = equipment("ACTIVE");
        when(equipmentRepository.findByHospitalIdOrderByCreatedAtDesc(HOSPITAL_ID)).thenReturn(List.of(eq));
        CalibrationRecord overdue = new CalibrationRecord();
        overdue.setDueDate(LocalDate.now().minusDays(1));
        when(calibrationRepository.findTopByHospitalIdAndEquipmentIdOrderByCalibrationDateDesc(HOSPITAL_ID, 20L))
                .thenReturn(Optional.of(overdue));

        List<MedicalEquipment> result = service.getEquipment();

        assertThat(result.get(0).getStatus()).isEqualTo("CALIBRATION_OVERDUE");
        verify(equipmentRepository).save(eq);
    }

    // ===== BR-4: double-close validation =====

    @Test
    void closeTicket_rejectedWithoutConfirmation() {
        when(securityHelper.getCurrentHospitalId()).thenReturn(HOSPITAL_ID);
        BreakdownTicket ticket = new BreakdownTicket();
        ticket.setId(7L);
        ticket.setHospitalId(HOSPITAL_ID);
        ticket.setEquipmentId(20L);
        ticket.setStatus("OPEN");
        when(ticketRepository.findByIdAndHospitalId(7L, HOSPITAL_ID)).thenReturn(Optional.of(ticket));

        TicketCloseRequest req = new TicketCloseRequest();
        req.setConfirmResolution(false);

        assertThatThrownBy(() -> service.closeTicket(7L, req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("BR-4");
        verify(equipmentRepository, never()).save(any());
    }

    @Test
    void closeTicket_rejectedWhenAlreadyClosed() {
        when(securityHelper.getCurrentHospitalId()).thenReturn(HOSPITAL_ID);
        BreakdownTicket ticket = new BreakdownTicket();
        ticket.setId(7L);
        ticket.setHospitalId(HOSPITAL_ID);
        ticket.setStatus("CLOSED");
        when(ticketRepository.findByIdAndHospitalId(7L, HOSPITAL_ID)).thenReturn(Optional.of(ticket));

        TicketCloseRequest req = new TicketCloseRequest();
        req.setConfirmResolution(true);

        assertThatThrownBy(() -> service.closeTicket(7L, req))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("already been closed");
    }

    @Test
    void closeTicket_confirmedResolution_restoresEquipmentToActive() {
        when(securityHelper.getCurrentHospitalId()).thenReturn(HOSPITAL_ID);
        BreakdownTicket ticket = new BreakdownTicket();
        ticket.setId(7L);
        ticket.setHospitalId(HOSPITAL_ID);
        ticket.setEquipmentId(20L);
        ticket.setStatus("OPEN");
        when(ticketRepository.findByIdAndHospitalId(7L, HOSPITAL_ID)).thenReturn(Optional.of(ticket));
        when(ticketRepository.save(any(BreakdownTicket.class))).thenAnswer(i -> i.getArgument(0));
        MedicalEquipment eq = equipment("DOWN");
        when(equipmentRepository.findByIdAndHospitalId(20L, HOSPITAL_ID)).thenReturn(Optional.of(eq));

        TicketCloseRequest req = new TicketCloseRequest();
        req.setConfirmResolution(true);

        BreakdownTicket saved = service.closeTicket(7L, req);

        assertThat(saved.getStatus()).isEqualTo("CLOSED");
        assertThat(saved.getResolvedAt()).isNotNull();
        assertThat(eq.getStatus()).isEqualTo("ACTIVE");
    }
}
