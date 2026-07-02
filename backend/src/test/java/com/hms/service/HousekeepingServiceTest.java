package com.hms.service;

import com.hms.dto.*;
import com.hms.entity.Bed;
import com.hms.entity.CleaningTask;
import com.hms.entity.FacilityComplaint;
import com.hms.entity.WasteCollection;
import com.hms.repository.AuditLogRepository;
import com.hms.repository.BedRepository;
import com.hms.repository.CleaningTaskRepository;
import com.hms.repository.FacilityComplaintRepository;
import com.hms.repository.WasteCollectionRepository;
import com.hms.security.SecurityContextHelper;
import com.hms.service.hospital.HousekeepingService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class HousekeepingServiceTest {

    @Mock private CleaningTaskRepository taskRepository;
    @Mock private WasteCollectionRepository wasteRepository;
    @Mock private FacilityComplaintRepository complaintRepository;
    @Mock private BedRepository bedRepository;
    @Mock private SecurityContextHelper securityHelper;
    @Mock private AuditLogRepository auditLogRepository;

    @InjectMocks
    private HousekeepingService service;

    private static final Long HOSPITAL_ID = 1L;

    private CleaningTask task(String status) {
        CleaningTask t = new CleaningTask();
        t.setId(5L);
        t.setHospitalId(HOSPITAL_ID);
        t.setLocation("BED-201");
        t.setTaskType("TERMINAL");
        t.setPriority("URGENT");
        t.setStatus(status);
        return t;
    }

    // ===== BR-1: verification releases the matching bed =====

    @Test
    void verifyTask_releasesMatchingBedToAvailable() {
        when(securityHelper.getCurrentHospitalId()).thenReturn(HOSPITAL_ID);
        when(securityHelper.getCurrentUserEmail()).thenReturn("supervisor@hospital.com");
        CleaningTask t = task("PENDING_VERIFICATION");
        when(taskRepository.findByIdAndHospitalId(5L, HOSPITAL_ID)).thenReturn(Optional.of(t));
        when(taskRepository.save(any(CleaningTask.class))).thenAnswer(i -> i.getArgument(0));
        Bed bed = new Bed();
        bed.setBedId(9L);
        bed.setHospitalId(HOSPITAL_ID);
        bed.setBedCode("BED-201");
        bed.setStatus("cleaning_required");
        when(bedRepository.findByHospitalIdAndBedCodeIgnoreCase(HOSPITAL_ID, "BED-201")).thenReturn(Optional.of(bed));

        TaskVerifyRequest req = new TaskVerifyRequest();
        req.setSupervisorSig("sig-data");

        CleaningTask saved = service.verifyTask(5L, req);

        assertThat(saved.getStatus()).isEqualTo("COMPLETED");
        assertThat(bed.getStatus()).isEqualTo("available");
        verify(bedRepository).save(bed);
    }

    @Test
    void verifyTask_rejectedWhenNotPendingVerification() {
        when(securityHelper.getCurrentHospitalId()).thenReturn(HOSPITAL_ID);
        CleaningTask t = task("DIRTY");
        when(taskRepository.findByIdAndHospitalId(5L, HOSPITAL_ID)).thenReturn(Optional.of(t));

        TaskVerifyRequest req = new TaskVerifyRequest();
        req.setSupervisorSig("sig-data");

        assertThatThrownBy(() -> service.verifyTask(5L, req))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not pending verification");
        verify(bedRepository, never()).save(any());
    }

    @Test
    void verifyTask_rejectedWithoutSignature() {
        when(securityHelper.getCurrentHospitalId()).thenReturn(HOSPITAL_ID);
        CleaningTask t = task("PENDING_VERIFICATION");
        when(taskRepository.findByIdAndHospitalId(5L, HOSPITAL_ID)).thenReturn(Optional.of(t));

        TaskVerifyRequest req = new TaskVerifyRequest();

        assertThatThrownBy(() -> service.verifyTask(5L, req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("BR-1");
    }

    @Test
    void completeTask_movesDirtyTaskToPendingVerification() {
        when(securityHelper.getCurrentHospitalId()).thenReturn(HOSPITAL_ID);
        CleaningTask t = task("DIRTY");
        when(taskRepository.findByIdAndHospitalId(5L, HOSPITAL_ID)).thenReturn(Optional.of(t));
        when(taskRepository.save(any(CleaningTask.class))).thenAnswer(i -> i.getArgument(0));

        CleaningTask saved = service.completeTask(5L);

        assertThat(saved.getStatus()).isEqualTo("PENDING_VERIFICATION");
        assertThat(saved.getCompletedAt()).isNotNull();
        assertThat(saved.getStartTime()).isNotNull();
    }

    @Test
    void completeTask_rejectedWhenAlreadyPendingVerification() {
        when(securityHelper.getCurrentHospitalId()).thenReturn(HOSPITAL_ID);
        when(taskRepository.findByIdAndHospitalId(5L, HOSPITAL_ID)).thenReturn(Optional.of(task("PENDING_VERIFICATION")));

        assertThatThrownBy(() -> service.completeTask(5L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("already been marked complete");
    }

    // ===== BR-3: waste weight validation =====

    @Test
    void logWaste_rejectedWhenWeightNotPositive() {
        when(securityHelper.getCurrentHospitalId()).thenReturn(HOSPITAL_ID);
        WasteCollectionRequest req = new WasteCollectionRequest();
        req.setWasteType("RED");
        req.setQuantity(BigDecimal.ZERO);
        req.setBarcodeTag("TAG-1");
        req.setVendor("BMW Ltd");

        assertThatThrownBy(() -> service.logWaste(req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must be positive");
        verify(wasteRepository, never()).save(any());
    }

    @Test
    void logWaste_savesValidEntry() {
        when(securityHelper.getCurrentHospitalId()).thenReturn(HOSPITAL_ID);
        when(securityHelper.getCurrentUserEmail()).thenReturn("collector@hospital.com");
        when(wasteRepository.save(any(WasteCollection.class))).thenAnswer(i -> i.getArgument(0));

        WasteCollectionRequest req = new WasteCollectionRequest();
        req.setWasteType("red");
        req.setQuantity(new BigDecimal("4.50"));
        req.setBarcodeTag("TAG-8876");
        req.setVendor("BMW Dispos Ltd");

        WasteCollection saved = service.logWaste(req);

        assertThat(saved.getWasteType()).isEqualTo("RED");
        assertThat(saved.getCollectorName()).isEqualTo("collector@hospital.com");
    }

    // ===== BR-4: double-close validation =====

    @Test
    void confirmComplaint_staysOpenUntilBothPartiesConfirm() {
        when(securityHelper.getCurrentHospitalId()).thenReturn(HOSPITAL_ID);
        when(securityHelper.getCurrentUserEmail()).thenReturn("engineer@hospital.com");
        FacilityComplaint complaint = new FacilityComplaint();
        complaint.setId(3L);
        complaint.setHospitalId(HOSPITAL_ID);
        complaint.setStatus("OPEN");
        when(complaintRepository.findByIdAndHospitalId(3L, HOSPITAL_ID)).thenReturn(Optional.of(complaint));
        when(complaintRepository.save(any(FacilityComplaint.class))).thenAnswer(i -> i.getArgument(0));

        ComplaintConfirmRequest engineerReq = new ComplaintConfirmRequest();
        engineerReq.setRole("ENGINEER");
        engineerReq.setResolution("AC gas refilled");

        FacilityComplaint afterEngineer = service.confirmComplaint(3L, engineerReq);
        assertThat(afterEngineer.getStatus()).isEqualTo("IN_PROGRESS");
        assertThat(afterEngineer.isEngineerConfirmed()).isTrue();
        assertThat(afterEngineer.isNurseConfirmed()).isFalse();

        ComplaintConfirmRequest nurseReq = new ComplaintConfirmRequest();
        nurseReq.setRole("NURSE");

        FacilityComplaint afterNurse = service.confirmComplaint(3L, nurseReq);
        assertThat(afterNurse.getStatus()).isEqualTo("CLOSED");
        assertThat(afterNurse.getResolvedAt()).isNotNull();
    }

    @Test
    void confirmComplaint_rejectedWhenAlreadyClosed() {
        when(securityHelper.getCurrentHospitalId()).thenReturn(HOSPITAL_ID);
        FacilityComplaint complaint = new FacilityComplaint();
        complaint.setId(3L);
        complaint.setHospitalId(HOSPITAL_ID);
        complaint.setStatus("CLOSED");
        when(complaintRepository.findByIdAndHospitalId(3L, HOSPITAL_ID)).thenReturn(Optional.of(complaint));

        ComplaintConfirmRequest req = new ComplaintConfirmRequest();
        req.setRole("NURSE");

        assertThatThrownBy(() -> service.confirmComplaint(3L, req))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("already been closed");
    }

    @Test
    void openComplaint_rejectedForInvalidComplaintType() {
        when(securityHelper.getCurrentHospitalId()).thenReturn(HOSPITAL_ID);
        FacilityComplaintRequest req = new FacilityComplaintRequest();
        req.setLocation("WARD-B-AC-02");
        req.setComplaintType("FIRE");

        assertThatThrownBy(() -> service.openComplaint(req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Complaint type");
        verify(complaintRepository, never()).save(any());
    }
}
