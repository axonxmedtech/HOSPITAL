package com.hms.service;

import com.hms.entity.AllergyMaster;
import com.hms.entity.PatientAllergy;
import com.hms.exception.ResourceNotFoundException;
import com.hms.repository.AllergyMasterRepository;
import com.hms.repository.PatientAllergyRepository;
import com.hms.security.SecurityContextHelper;
import com.hms.service.hospital.PatientAllergyService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PatientAllergyServiceTest {

    @Mock private PatientAllergyRepository patientAllergyRepo;
    @Mock private AllergyMasterRepository allergyMasterRepo;
    @Mock private SecurityContextHelper securityHelper;

    @InjectMocks
    private PatientAllergyService patientAllergyService;

    @Test
    void addAllergy_savesSuccessfully() {
        Long hospitalId = 1L, patientId = 10L, allergyMasterId = 5L, userId = 99L;
        when(securityHelper.getCurrentHospitalId()).thenReturn(hospitalId);
        when(securityHelper.getCurrentUserId()).thenReturn(userId);
        AllergyMaster master = new AllergyMaster();
        master.setId(allergyMasterId); master.setHospitalId(hospitalId);
        when(allergyMasterRepo.findById(allergyMasterId)).thenReturn(Optional.of(master));
        when(patientAllergyRepo.existsByPatientIdAndAllergyMasterId(patientId, allergyMasterId)).thenReturn(false);
        when(patientAllergyRepo.save(any())).thenAnswer(inv -> {
            PatientAllergy saved = inv.getArgument(0);
            saved.setId(1L);
            return saved;
        });

        PatientAllergy result = patientAllergyService.addAllergy(patientId, allergyMasterId, "SEVERE", "Anaphylaxis history");

        assertThat(result.getPatientId()).isEqualTo(patientId);
        assertThat(result.getSeverity()).isEqualTo("SEVERE");
        assertThat(result.getRecordedByUserId()).isEqualTo(userId);
        assertThat(result.getNotes()).isEqualTo("Anaphylaxis history");
    }

    @Test
    void addAllergy_nullSeverity_defaultsToUnknown() {
        Long hospitalId = 1L, patientId = 10L, allergyMasterId = 5L;
        when(securityHelper.getCurrentHospitalId()).thenReturn(hospitalId);
        when(securityHelper.getCurrentUserId()).thenReturn(1L);
        AllergyMaster master = new AllergyMaster();
        master.setId(allergyMasterId); master.setHospitalId(hospitalId);
        when(allergyMasterRepo.findById(allergyMasterId)).thenReturn(Optional.of(master));
        when(patientAllergyRepo.existsByPatientIdAndAllergyMasterId(patientId, allergyMasterId)).thenReturn(false);
        when(patientAllergyRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        PatientAllergy result = patientAllergyService.addAllergy(patientId, allergyMasterId, null, null);

        assertThat(result.getSeverity()).isEqualTo("UNKNOWN");
    }

    @Test
    void addAllergy_throwsIfDuplicate() {
        Long hospitalId = 1L, patientId = 10L, allergyMasterId = 5L;
        when(securityHelper.getCurrentHospitalId()).thenReturn(hospitalId);
        AllergyMaster master = new AllergyMaster();
        master.setId(allergyMasterId); master.setHospitalId(hospitalId);
        when(allergyMasterRepo.findById(allergyMasterId)).thenReturn(Optional.of(master));
        when(patientAllergyRepo.existsByPatientIdAndAllergyMasterId(patientId, allergyMasterId)).thenReturn(true);

        assertThatThrownBy(() -> patientAllergyService.addAllergy(patientId, allergyMasterId, "MILD", null))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("already recorded");
    }

    @Test
    void addAllergy_wrongHospitalAllergy_throwsNotFound() {
        Long hospitalId = 1L, patientId = 10L, allergyMasterId = 5L;
        when(securityHelper.getCurrentHospitalId()).thenReturn(hospitalId);
        AllergyMaster master = new AllergyMaster();
        master.setId(allergyMasterId); master.setHospitalId(999L); // different hospital
        when(allergyMasterRepo.findById(allergyMasterId)).thenReturn(Optional.of(master));

        assertThatThrownBy(() -> patientAllergyService.addAllergy(patientId, allergyMasterId, "MILD", null))
            .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void getPatientAllergies_returnsAll() {
        Long hospitalId = 1L, patientId = 10L;
        when(securityHelper.getCurrentHospitalId()).thenReturn(hospitalId);
        PatientAllergy pa = new PatientAllergy();
        pa.setPatientId(patientId); pa.setSeverity("MODERATE");
        when(patientAllergyRepo.findByPatientIdAndHospitalId(patientId, hospitalId)).thenReturn(List.of(pa));

        List<PatientAllergy> result = patientAllergyService.getPatientAllergies(patientId);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getSeverity()).isEqualTo("MODERATE");
    }

    @Test
    void removeAllergy_deletesSuccessfully() {
        Long hospitalId = 1L, patientId = 10L, allergyId = 20L;
        when(securityHelper.getCurrentHospitalId()).thenReturn(hospitalId);
        PatientAllergy pa = new PatientAllergy();
        pa.setId(allergyId); pa.setPatientId(patientId); pa.setHospitalId(hospitalId);
        when(patientAllergyRepo.findById(allergyId)).thenReturn(Optional.of(pa));

        patientAllergyService.removeAllergy(patientId, allergyId);

        verify(patientAllergyRepo).delete(pa);
    }

    @Test
    void removeAllergy_wrongPatient_throwsNotFound() {
        Long hospitalId = 1L, patientId = 10L, allergyId = 20L;
        when(securityHelper.getCurrentHospitalId()).thenReturn(hospitalId);
        PatientAllergy pa = new PatientAllergy();
        pa.setId(allergyId); pa.setPatientId(999L); // different patient
        pa.setHospitalId(hospitalId);
        when(patientAllergyRepo.findById(allergyId)).thenReturn(Optional.of(pa));

        assertThatThrownBy(() -> patientAllergyService.removeAllergy(patientId, allergyId))
            .isInstanceOf(ResourceNotFoundException.class);
    }
}
