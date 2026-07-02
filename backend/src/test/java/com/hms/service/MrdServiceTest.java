package com.hms.service;

import com.hms.dto.MrdCompletenessDTO;
import com.hms.dto.TimelineEventDTO;
import com.hms.entity.DischargeSummary;
import com.hms.entity.IpdAdmission;
import com.hms.entity.MrdRecord;
import com.hms.entity.Patient;
import com.hms.entity.VitalSigns;
import com.hms.exception.UnauthorizedException;
import com.hms.repository.*;
import com.hms.security.SecurityContextHelper;
import com.hms.service.hospital.MrdService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MrdServiceTest {

    @Mock private PatientRepository patientRepository;
    @Mock private DoctorRepository doctorRepository;
    @Mock private UserRepository userRepository;
    @Mock private MrdRecordRepository mrdRecordRepository;
    @Mock private IpdAdmissionRepository ipdAdmissionRepository;
    @Mock private SecurityContextHelper securityHelper;
    @Mock private DischargeSummaryRepository dischargeSummaryRepository;
    @Mock private VitalSignsRepository vitalSignsRepository;
    @Mock private DoctorRoundRepository doctorRoundRepository;
    @Mock private PatientConsentRepository patientConsentRepository;
    @Mock private ClinicalAssessmentRepository clinicalAssessmentRepository;
    @Mock private EmergencyVisitRepository emergencyVisitRepository;
    @Mock private AuditLogService auditLogService;

    @InjectMocks
    private MrdService service;

    private IpdAdmission discharged(Long id, Long hospitalId) {
        IpdAdmission ipd = new IpdAdmission();
        ipd.setId(id);
        ipd.setHospitalId(hospitalId);
        ipd.setStatus("DISCHARGED");
        return ipd;
    }

    @Test
    void computeCompleteness_flagsMissingDocuments() {
        Long hospitalId = 1L, ipdId = 10L;
        when(securityHelper.getCurrentHospitalId()).thenReturn(hospitalId);
        when(ipdAdmissionRepository.findById(ipdId)).thenReturn(Optional.of(discharged(ipdId, hospitalId)));
        // Only vitals exist; everything else missing
        when(vitalSignsRepository.findByIpdAdmissionIdOrderByRecordedAtDesc(ipdId))
                .thenReturn(List.of(new VitalSigns()));

        MrdCompletenessDTO dto = service.computeCompleteness(ipdId);

        assertThat(dto.isComplete()).isFalse();
        assertThat(dto.getItems().get("VITALS_CHART")).isEqualTo("PASS");
        assertThat(dto.getItems().get("DISCHARGE_SUMMARY")).isEqualTo("FAIL");
        assertThat(dto.getItems().get("CONSENT")).isEqualTo("FAIL");
    }

    @Test
    void archive_blockedWhenIncompleteWithoutOverride() {
        Long hospitalId = 1L, ipdId = 10L;
        when(securityHelper.getCurrentHospitalId()).thenReturn(hospitalId);
        when(securityHelper.getCurrentUserId()).thenReturn(2L);
        when(ipdAdmissionRepository.findById(ipdId)).thenReturn(Optional.of(discharged(ipdId, hospitalId)));
        when(mrdRecordRepository.findByIpdAdmissionId(ipdId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.archiveAdmission(ipdId, "Rack A1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("incomplete");
        verify(mrdRecordRepository, never()).save(any());
    }

    @Test
    void archive_incompleteWithOverrideIsAuditedAndProceeds() {
        Long hospitalId = 1L, ipdId = 10L;
        when(securityHelper.getCurrentHospitalId()).thenReturn(hospitalId);
        when(securityHelper.getCurrentUserId()).thenReturn(2L);
        when(securityHelper.getCurrentUserEmail()).thenReturn("mrd@hospital.com");
        when(ipdAdmissionRepository.findById(ipdId)).thenReturn(Optional.of(discharged(ipdId, hospitalId)));
        when(mrdRecordRepository.findByIpdAdmissionId(ipdId)).thenReturn(Optional.empty());
        when(mrdRecordRepository.findMaxMrdSequence()).thenReturn(4);
        when(mrdRecordRepository.save(any(MrdRecord.class))).thenAnswer(inv -> inv.getArgument(0));

        MrdRecord record = service.archiveAdmission(ipdId, "Rack A1", "Legacy pre-system file, documents on paper");

        assertThat(record.getMrdNumber()).isEqualTo("MRD-5");
        verify(auditLogService).logAction(eq("MRD_INCOMPLETE_OVERRIDE"), anyString(), eq("mrd@hospital.com"),
                eq(hospitalId), eq("MRD"), eq("10"), anyString());
    }

    @Test
    void archive_completeFileNeedsNoOverride() {
        Long hospitalId = 1L, ipdId = 10L;
        when(securityHelper.getCurrentHospitalId()).thenReturn(hospitalId);
        when(securityHelper.getCurrentUserId()).thenReturn(2L);
        when(ipdAdmissionRepository.findById(ipdId)).thenReturn(Optional.of(discharged(ipdId, hospitalId)));
        when(mrdRecordRepository.findByIpdAdmissionId(ipdId)).thenReturn(Optional.empty());
        when(mrdRecordRepository.findMaxMrdSequence()).thenReturn(null);
        when(mrdRecordRepository.save(any(MrdRecord.class))).thenAnswer(inv -> inv.getArgument(0));
        // Everything present
        when(dischargeSummaryRepository.findByIpdAdmissionId(ipdId)).thenReturn(Optional.of(new DischargeSummary()));
        when(vitalSignsRepository.findByIpdAdmissionIdOrderByRecordedAtDesc(ipdId)).thenReturn(List.of(new VitalSigns()));
        when(doctorRoundRepository.findByIpdAdmissionIdAndHospitalIdOrderByRoundDateTimeDesc(ipdId, hospitalId))
                .thenReturn(List.of(new com.hms.entity.DoctorRound()));
        when(patientConsentRepository.findByHospitalIdAndAdmissionIdAndIsDeletedFalse(hospitalId, ipdId))
                .thenReturn(List.of(new com.hms.entity.PatientConsent()));
        when(clinicalAssessmentRepository.findFirstByHospitalIdAndAdmissionIdAndStatusNotInOrderByVersionDesc(
                eq(hospitalId), eq(ipdId), anyList()))
                .thenReturn(Optional.of(new com.hms.entity.ClinicalAssessment()));

        MrdRecord record = service.archiveAdmission(ipdId, "Rack B2");

        assertThat(record.getStatus()).isEqualTo("ARCHIVED");
        verify(auditLogService, never()).logAction(eq("MRD_INCOMPLETE_OVERRIDE"), anyString(), anyString(),
                anyLong(), anyString(), anyString(), anyString());
    }

    @Test
    void timeline_mergesAdmissionsAndEmergencyChronologically() {
        Long hospitalId = 1L, patientId = 50L;
        when(securityHelper.getCurrentHospitalId()).thenReturn(hospitalId);
        Patient patient = new Patient();
        patient.setId(patientId);
        patient.setHospitalId(hospitalId);
        when(patientRepository.findById(patientId)).thenReturn(Optional.of(patient));

        IpdAdmission adm = new IpdAdmission();
        adm.setId(1L);
        adm.setHospitalId(hospitalId);
        adm.setIpdNumber("IPD-1");
        adm.setAdmissionDatetime(LocalDateTime.now().minusDays(3));
        adm.setDischargeDatetime(LocalDateTime.now().minusDays(1));
        when(ipdAdmissionRepository.findByPatientIdOrderByAdmissionDatetimeDesc(patientId)).thenReturn(List.of(adm));

        com.hms.entity.EmergencyVisit ev = new com.hms.entity.EmergencyVisit();
        ev.setId(2L);
        ev.setEmergencyNumber("ER-1");
        ev.setArrivalTime(LocalDateTime.now().minusDays(4));
        when(emergencyVisitRepository.findByPatientIdAndHospitalIdOrderByArrivalTimeDesc(patientId, hospitalId))
                .thenReturn(List.of(ev));

        List<TimelineEventDTO> timeline = service.getPatientTimeline(patientId);

        assertThat(timeline).hasSize(3); // admission + discharge + emergency
        assertThat(timeline.get(0).getType()).isEqualTo("DISCHARGE");   // most recent first
        assertThat(timeline.get(2).getType()).isEqualTo("EMERGENCY");   // oldest last
    }

    @Test
    void timeline_rejectsCrossTenantPatient() {
        when(securityHelper.getCurrentHospitalId()).thenReturn(1L);
        Patient foreign = new Patient();
        foreign.setId(50L);
        foreign.setHospitalId(99L);
        when(patientRepository.findById(50L)).thenReturn(Optional.of(foreign));

        assertThatThrownBy(() -> service.getPatientTimeline(50L))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("not found under hospital tenant");
    }

    @Test
    void completeness_requiresTenantContext() {
        when(securityHelper.getCurrentHospitalId()).thenReturn(null);
        assertThatThrownBy(() -> service.computeCompleteness(1L))
                .isInstanceOf(UnauthorizedException.class);
    }
}
