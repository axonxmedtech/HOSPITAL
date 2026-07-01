package com.hms.service;

import com.hms.entity.*;
import com.hms.exception.ResourceNotFoundException;
import com.hms.exception.UnauthorizedException;
import com.hms.repository.*;
import com.hms.security.SecurityContextHelper;
import com.hms.service.hospital.NursingProgressService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NursingProgressServiceTest {

    @Mock private NursingProgressNoteRepository progressNoteRepository;
    @Mock private NursingProcedureRepository procedureRepository;
    @Mock private ShiftHandoverRepository handoverRepository;
    @Mock private IpdAdmissionRepository ipdAdmissionRepository;
    @Mock private PatientRepository patientRepository;
    @Mock private SecurityContextHelper securityHelper;

    @InjectMocks private NursingProgressService progressService;

    @Test
    void createProgressNote_success() {
        when(securityHelper.getCurrentHospitalId()).thenReturn(1L);
        when(securityHelper.getCurrentUserId()).thenReturn(15L);

        IpdAdmission admission = new IpdAdmission();
        admission.setId(200L);
        admission.setHospitalId(1L);
        admission.setPatientId(100L);

        when(ipdAdmissionRepository.findById(200L)).thenReturn(Optional.of(admission));
        when(progressNoteRepository.findByHospitalIdAndAdmissionIdAndShift(1L, 200L, "MORNING"))
                .thenReturn(Optional.empty());

        when(progressNoteRepository.save(any(NursingProgressNote.class))).thenAnswer(invocation -> invocation.getArgument(0));

        NursingProgressNote result = progressService.createProgressNote(
                200L, "MORNING", "Stable", 3, "No issues", false, null, null, "STABLE"
        );

        assertThat(result).isNotNull();
        assertThat(result.getShift()).isEqualTo("MORNING");
        assertThat(result.getStatus()).isEqualTo("DRAFT");
        verify(progressNoteRepository).save(any(NursingProgressNote.class));
    }

    @Test
    void createProgressNote_invalidShift_throwsException() {
        when(securityHelper.getCurrentHospitalId()).thenReturn(1L);

        assertThatThrownBy(() -> progressService.createProgressNote(
                200L, "MIDDAY", "Stable", 3, "No issues", false, null, null, "STABLE"
        )).isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("Shift must be MORNING, EVENING, or NIGHT");
    }

    @Test
    void updateProgressNote_nonDraft_throwsException() {
        when(securityHelper.getCurrentHospitalId()).thenReturn(1L);

        NursingProgressNote note = new NursingProgressNote();
        note.setId(50L);
        note.setHospitalId(1L);
        note.setStatus("SUBMITTED"); // locked

        when(progressNoteRepository.findById(50L)).thenReturn(Optional.of(note));

        assertThatThrownBy(() -> progressService.updateProgressNote(
                50L, "Stable", 3, "Good", false, null, null, "STABLE", "SUBMITTED"
        )).isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("Only draft notes can be updated");
    }

    @Test
    void recordProcedure_success() {
        when(securityHelper.getCurrentHospitalId()).thenReturn(1L);
        when(securityHelper.getCurrentUserId()).thenReturn(15L);

        NursingProgressNote note = new NursingProgressNote();
        note.setId(50L);
        note.setHospitalId(1L);

        when(progressNoteRepository.findById(50L)).thenReturn(Optional.of(note));
        when(procedureRepository.save(any(NursingProcedure.class))).thenAnswer(invocation -> invocation.getArgument(0));

        NursingProcedure result = progressService.recordProcedure(50L, "Dressing Change", "Clean wound");

        assertThat(result).isNotNull();
        assertThat(result.getProcedureName()).isEqualTo("Dressing Change");
        verify(procedureRepository).save(any(NursingProcedure.class));
    }

    @Test
    void recordHandover_success() {
        when(securityHelper.getCurrentHospitalId()).thenReturn(1L);
        when(securityHelper.getCurrentUserId()).thenReturn(15L);

        IpdAdmission admission = new IpdAdmission();
        admission.setId(200L);
        admission.setHospitalId(1L);

        when(ipdAdmissionRepository.findById(200L)).thenReturn(Optional.of(admission));
        when(handoverRepository.save(any(ShiftHandover.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ShiftHandover result = progressService.recordHandover(
                200L, "NIGHT", 22L, "Meds given", "None", "Meds due at 6am", "None", false
        );

        assertThat(result).isNotNull();
        assertThat(result.getShift()).isEqualTo("NIGHT");
        assertThat(result.getIncomingNurseId()).isEqualTo(22L);
        verify(handoverRepository).save(any(ShiftHandover.class));
    }
}
