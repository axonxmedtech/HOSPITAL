package com.hms.service;

import com.hms.entity.*;
import com.hms.exception.ResourceNotFoundException;
import com.hms.repository.*;
import com.hms.security.SecurityContextHelper;
import com.hms.service.hospital.MasterDataService;
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
class MasterDataServiceTest {

    @Mock private LabTestMasterRepository labTestRepo;
    @Mock private RadiologyTestMasterRepository radiologyTestRepo;
    @Mock private AllergyMasterRepository allergyRepo;
    @Mock private DiagnosisMasterRepository diagnosisRepo;
    @Mock private ProcedureMasterRepository procedureRepo;
    @Mock private SecurityContextHelper securityHelper;

    @InjectMocks
    private MasterDataService masterDataService;

    @Test
    void searchLabTests_returnsMatchingResults() {
        Long hospitalId = 1L;
        when(securityHelper.getCurrentHospitalId()).thenReturn(hospitalId);
        LabTestMaster cbc = new LabTestMaster();
        cbc.setId(1L); cbc.setTestName("CBC"); cbc.setHospitalId(hospitalId);
        when(labTestRepo.searchByHospital(hospitalId, "cbc")).thenReturn(List.of(cbc));

        List<LabTestMaster> result = masterDataService.searchLabTests("cbc");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getTestName()).isEqualTo("CBC");
    }

    @Test
    void searchLabTests_emptyQuery_returnsAll() {
        Long hospitalId = 1L;
        when(securityHelper.getCurrentHospitalId()).thenReturn(hospitalId);
        when(labTestRepo.findByHospitalIdAndIsActiveTrueOrderByTestNameAsc(hospitalId)).thenReturn(List.of());

        List<LabTestMaster> result = masterDataService.searchLabTests("");

        verify(labTestRepo).findByHospitalIdAndIsActiveTrueOrderByTestNameAsc(hospitalId);
        verify(labTestRepo, never()).searchByHospital(any(), any());
    }

    @Test
    void createLabTest_savesAndReturns() {
        Long hospitalId = 1L;
        when(securityHelper.getCurrentHospitalId()).thenReturn(hospitalId);
        LabTestMaster input = new LabTestMaster();
        input.setTestName("LFT");
        input.setSampleType("BLOOD");
        input.setDepartment("BIOCHEMISTRY");
        when(labTestRepo.save(any())).thenAnswer(inv -> {
            LabTestMaster saved = inv.getArgument(0);
            saved.setId(10L);
            return saved;
        });

        LabTestMaster result = masterDataService.createLabTest(input);

        assertThat(result.getId()).isEqualTo(10L);
        assertThat(result.getHospitalId()).isEqualTo(hospitalId);
        assertThat(result.getIsActive()).isTrue();
        verify(labTestRepo).save(any(LabTestMaster.class));
    }

    @Test
    void deactivateLabTest_setsIsActiveFalse() {
        Long hospitalId = 1L;
        when(securityHelper.getCurrentHospitalId()).thenReturn(hospitalId);
        LabTestMaster existing = new LabTestMaster();
        existing.setId(5L); existing.setHospitalId(hospitalId); existing.setIsActive(true);
        when(labTestRepo.findById(5L)).thenReturn(Optional.of(existing));
        when(labTestRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        masterDataService.deactivateLabTest(5L);

        assertThat(existing.getIsActive()).isFalse();
        verify(labTestRepo).save(existing);
    }

    @Test
    void deactivateLabTest_wrongHospital_throwsNotFound() {
        Long hospitalId = 1L;
        when(securityHelper.getCurrentHospitalId()).thenReturn(hospitalId);
        LabTestMaster existing = new LabTestMaster();
        existing.setId(5L); existing.setHospitalId(999L); // different hospital
        when(labTestRepo.findById(5L)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> masterDataService.deactivateLabTest(5L))
            .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void searchDiagnoses_returnsMatchingIcdResults() {
        Long hospitalId = 1L;
        when(securityHelper.getCurrentHospitalId()).thenReturn(hospitalId);
        DiagnosisMaster dm = new DiagnosisMaster();
        dm.setIcdCode("I10"); dm.setIcdDescription("Hypertension"); dm.setHospitalId(hospitalId);
        when(diagnosisRepo.searchByHospital(hospitalId, "hypert")).thenReturn(List.of(dm));

        List<DiagnosisMaster> result = masterDataService.searchDiagnoses("hypert");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getIcdCode()).isEqualTo("I10");
    }

    @Test
    void createAllergy_setsIsCustomTrue() {
        Long hospitalId = 1L;
        when(securityHelper.getCurrentHospitalId()).thenReturn(hospitalId);
        AllergyMaster input = new AllergyMaster();
        input.setAllergyName("Mango");
        input.setCategory("FOOD");
        when(allergyRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        AllergyMaster result = masterDataService.createAllergy(input);

        assertThat(result.getIsCustom()).isTrue();
        assertThat(result.getIsActive()).isTrue();
        assertThat(result.getHospitalId()).isEqualTo(hospitalId);
    }
}
