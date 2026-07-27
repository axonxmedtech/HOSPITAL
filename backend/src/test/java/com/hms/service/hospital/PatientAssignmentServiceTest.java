package com.hms.service.hospital;

import com.hms.entity.HospitalSetting;
import com.hms.entity.IpdAdmission;
import com.hms.entity.NurseProfile;
import com.hms.repository.HospitalSettingRepository;
import com.hms.repository.NurseProfileRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PatientAssignmentServiceTest {

    @Mock HospitalSettingRepository hospitalSettingRepository;
    @Mock NurseProfileRepository nurseProfileRepository;
    @Mock NurseAssignmentService nurseAssignmentService;
    @Mock NurseCoverageService coverageService;

    @InjectMocks PatientAssignmentService service;

    private IpdAdmission admission() {
        IpdAdmission a = new IpdAdmission();
        a.setId(1L); a.setHospitalId(7L); a.setPatientId(500L); a.setWardId(3L);
        return a;
    }
    private NurseProfile nurse(Long id, Long userId, boolean incharge) {
        NurseProfile p = new NurseProfile();
        p.setId(id); p.setUserId(userId); p.setHospitalId(7L); p.setWardId(3L);
        p.setIsIncharge(incharge); p.setIsActive(true);
        return p;
    }
    private void loginMode(boolean on) {
        HospitalSetting s = new HospitalSetting(); s.setSeparateNurseLogin(on);
        when(hospitalSettingRepository.findByHospital_Id(7L)).thenReturn(Optional.of(s));
    }

    @Test
    void loginOff_noAssignment() {
        loginMode(false);
        service.onAdmission(admission());
        verify(nurseAssignmentService, never()).assignNurse(anyLong(), anyLong(), any());
    }

    @Test
    void loginOn_oneStaffNurse_autoAssigns() {
        loginMode(true);
        when(coverageService.effectiveWardNurses(eq(3L), any()))
                .thenReturn(List.of(nurse(11L, 20L, false)));
        service.onAdmission(admission());
        verify(nurseAssignmentService).assignNurse(eq(1L), eq(20L), any());
    }

    @Test
    void loginOn_multipleStaffNurses_noAutoAssign() {
        loginMode(true);
        when(coverageService.effectiveWardNurses(eq(3L), any()))
                .thenReturn(List.of(nurse(11L, 20L, false), nurse(12L, 21L, false)));
        service.onAdmission(admission());
        verify(nurseAssignmentService, never()).assignNurse(anyLong(), anyLong(), any());
    }

    @Test
    void loginOn_zeroStaffNurses_noAutoAssign() {
        loginMode(true);
        when(coverageService.effectiveWardNurses(eq(3L), any())).thenReturn(List.of());
        service.onAdmission(admission());
        verify(nurseAssignmentService, never()).assignNurse(anyLong(), anyLong(), any());
    }
}
