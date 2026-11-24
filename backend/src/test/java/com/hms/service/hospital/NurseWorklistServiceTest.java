package com.hms.service.hospital;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;

import com.hms.dto.MyPatientDTO;
import com.hms.dto.NurseWorklistItemDTO;
import com.hms.entity.LabOrder;
import com.hms.entity.MedicationAdministration;
import com.hms.entity.Prescription;
import com.hms.entity.VitalsRecord;
import com.hms.repository.LabOrderRepository;
import com.hms.repository.MedicationAdministrationRepository;
import com.hms.repository.PrescriptionRepository;
import com.hms.repository.VitalsRecordRepository;
import com.hms.security.SecurityContextHelper;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class NurseWorklistServiceTest {

    @Mock NurseWorkspaceService nurseWorkspaceService;
    @Mock PrescriptionRepository prescriptionRepository;
    @Mock MedicationAdministrationRepository marRepository;
    @Mock VitalsRecordRepository vitalsRepository;
    @Mock LabOrderRepository labOrderRepository;
    @Mock SecurityContextHelper securityHelper;

    @InjectMocks NurseWorklistService service;

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 24);

    @Test
    void frequencyBecomesTheDaysDoseSlots() {
        assertThat(NurseWorklistService.slotsFor("1-0-1")).containsExactly("MORNING", "NIGHT");
        assertThat(NurseWorklistService.slotsFor("1-1-1")).containsExactly("MORNING", "AFTERNOON", "NIGHT");
        assertThat(NurseWorklistService.slotsFor("0-0-2")).containsExactly("NIGHT");
        assertThat(NurseWorklistService.slotsFor("BD")).containsExactly("MORNING", "NIGHT");
        assertThat(NurseWorklistService.slotsFor("As Per Required")).isEmpty();
        assertThat(NurseWorklistService.slotsFor("every 6 hours")).isEmpty();
        assertThat(NurseWorklistService.isAsNeeded("As Per Required")).isTrue();
        assertThat(NurseWorklistService.isAsNeeded("SOS")).isTrue();
    }

    @Test
    void aDoseIsDueAnHourEitherSideOfItsTime_thenOverdue() {
        LocalDateTime eight = TODAY.atTime(8, 0);
        assertThat(NurseWorklistService.stateAt(eight, TODAY.atTime(6, 59))).isEqualTo("UPCOMING");
        assertThat(NurseWorklistService.stateAt(eight, TODAY.atTime(7, 0))).isEqualTo("DUE");
        assertThat(NurseWorklistService.stateAt(eight, TODAY.atTime(9, 0))).isEqualTo("DUE");
        assertThat(NurseWorklistService.stateAt(eight, TODAY.atTime(9, 1))).isEqualTo("OVERDUE");
    }

    @Test
    void aRecordClosesItsOwnDose_andOneWithoutATimeClosesTheEarliestOpenDose() {
        MedicationAdministration night = mar("GIVEN", TODAY.atTime(20, 0));
        MedicationAdministration fromChart = mar("GIVEN", null);
        MedicationAdministration undoneOrOther = mar("UNKNOWN", null);
        Map<LocalDateTime, MedicationAdministration> closed = NurseWorklistService.matchRecords(
                List.of("MORNING", "NIGHT"), List.of(night, fromChart, undoneOrOther), TODAY);
        assertThat(closed).containsEntry(TODAY.atTime(20, 0), night).containsEntry(TODAY.atTime(8, 0), fromChart);
        assertThat(closed).hasSize(2);
    }

    @Test
    void doseTimesThatPassedBeforeTheOrderWasWrittenAreNotOwed() {
        Prescription rx = new Prescription();
        rx.setCreatedAt(TODAY.atTime(15, 0));
        assertThat(NurseWorklistService.beforeTheOrder(rx, TODAY.atTime(8, 0))).isTrue();
        assertThat(NurseWorklistService.beforeTheOrder(rx, TODAY.atTime(14, 0))).isFalse(); // within the hour
        assertThat(NurseWorklistService.beforeTheOrder(rx, TODAY.atTime(20, 0))).isFalse();
        rx.setCreatedAt(TODAY.minusDays(1).atTime(15, 0));
        assertThat(NurseWorklistService.beforeTheOrder(rx, TODAY.atTime(8, 0))).isFalse();
    }

    @Test
    void anOrderCountsOnlyFromItsStartAndForItsDays() {
        Prescription rx = new Prescription();
        rx.setStartDate(TODAY.minusDays(2));
        rx.setDurationDays(3);
        assertThat(NurseWorklistService.onCourse(rx, TODAY)).isTrue();
        assertThat(NurseWorklistService.onCourse(rx, TODAY.plusDays(1))).isFalse();
        rx.setStartDate(TODAY.plusDays(1));
        assertThat(NurseWorklistService.onCourse(rx, TODAY)).isFalse();
    }

    @Test
    void theListHasEachDoseVitalsAndSamplesForThePatientsTheNurseSees() {
        when(securityHelper.getCurrentHospitalId()).thenReturn(1L);
        MyPatientDTO p = new MyPatientDTO();
        p.setIpdAdmissionId(5L);
        p.setPatientName("Test Patient");
        p.setBedCode("A-1");
        when(nurseWorkspaceService.getMyPatients()).thenReturn(List.of(p));

        Prescription bd = new Prescription();
        bd.setId(11L);
        bd.setHospitalId(1L);
        bd.setMedicineName("Paracetamol");
        bd.setFrequency("1-0-1");
        bd.setStartDate(TODAY);
        bd.setDurationDays(5);
        Prescription sos = new Prescription();
        sos.setId(12L);
        sos.setHospitalId(1L);
        sos.setMedicineName("Ondansetron");
        sos.setFrequency("As Per Required");
        when(prescriptionRepository.findByIpdAdmissionIdAndStatus(5L, "ACTIVE")).thenReturn(List.of(bd, sos));

        MedicationAdministration morning = mar("GIVEN", TODAY.atTime(8, 0));
        morning.setPrescriptionId(11L);
        when(marRepository.findByIpdAdmissionIdInAndIsActiveTrueOrderByCreatedAtAsc(anyList())).thenReturn(List.of(morning));

        VitalsRecord v = new VitalsRecord();
        v.setIpdAdmissionId(5L);
        v.setRecordedAt(TODAY.atTime(6, 0));
        when(vitalsRepository.findLatestForAdmissions(anyList())).thenReturn(List.of(v));

        LabOrder cbc = new LabOrder();
        cbc.setPublicId("lab-1");
        cbc.setTestName("CBC");
        cbc.setStatus("ORDERED");
        LabOrder done = new LabOrder();
        done.setStatus("COMPLETED");
        when(labOrderRepository.findByHospitalIdAndIpdAdmissionIdOrderByCreatedAtDescIdDesc(1L, 5L)).thenReturn(List.of(cbc, done));

        List<NurseWorklistItemDTO> list = service.worklist(TODAY.atTime(13, 30));

        assertThat(list).extracting(NurseWorklistItemDTO::getKind, NurseWorklistItemDTO::getSlot, NurseWorklistItemDTO::getState)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("DOSE", "MORNING", "DONE"),
                        org.assertj.core.groups.Tuple.tuple("DOSE", "NIGHT", "UPCOMING"),
                        org.assertj.core.groups.Tuple.tuple("DOSE", null, "AS_NEEDED"),
                        org.assertj.core.groups.Tuple.tuple("VITALS", null, "OVERDUE"), // last at 06:00, due again 12:00
                        org.assertj.core.groups.Tuple.tuple("SAMPLE", null, "DUE"));
        assertThat(list.get(0).getRecordedStatus()).isEqualTo("GIVEN");
        assertThat(list.get(4).getInvestigationPublicId()).isEqualTo("lab-1");
        assertThat(list.get(1).getBedCode()).isEqualTo("A-1");
    }

    private static MedicationAdministration mar(String status, LocalDateTime scheduled) {
        MedicationAdministration m = new MedicationAdministration();
        m.setStatus(status);
        m.setScheduledTime(scheduled);
        m.setAdministeredTime(scheduled != null ? scheduled : TODAY.atTime(8, 5));
        m.setCreatedAt(TODAY.atTime(8, 5));
        return m;
    }
}
