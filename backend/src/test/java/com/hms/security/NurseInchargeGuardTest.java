package com.hms.security;

import com.hms.entity.NurseProfile;
import com.hms.entity.Ward;
import com.hms.repository.IpdAdmissionRepository;
import com.hms.repository.NurseProfileRepository;
import com.hms.repository.WardRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NurseInchargeGuardTest {

    @Mock NurseProfileRepository nurseProfileRepository;
    @Mock WardRepository wardRepository;
    @Mock IpdAdmissionRepository ipdAdmissionRepository;
    @Mock SecurityContextHelper securityHelper;

    @InjectMocks NurseInchargeGuard guard;

    private Ward ward(Long id, Long hospitalId, Long inchargeNurseId) {
        Ward w = new Ward(); w.setWardId(id); w.setHospitalId(hospitalId); w.setInchargeNurseId(inchargeNurseId);
        return w;
    }

    @Test
    void admin_hasAccessToAnyWard() {
        when(securityHelper.getCurrentUserRole()).thenReturn("HOSPITAL_ADMIN");
        assertThatCode(() -> guard.assertWardAccess(3L)).doesNotThrowAnyException();
    }

    @Test
    void incharge_allowedForOwnWard() {
        when(securityHelper.getCurrentUserRole()).thenReturn("NURSE_INCHARGE");
        when(securityHelper.getCurrentUserId()).thenReturn(20L);
        NurseProfile me = new NurseProfile(); me.setId(11L); me.setHospitalId(7L); me.setIsIncharge(true);
        when(nurseProfileRepository.findByUserId(20L)).thenReturn(Optional.of(me));
        when(wardRepository.findById(3L)).thenReturn(Optional.of(ward(3L, 7L, 11L)));
        assertThatCode(() -> guard.assertWardAccess(3L)).doesNotThrowAnyException();
    }

    @Test
    void incharge_deniedForOtherWard() {
        when(securityHelper.getCurrentUserRole()).thenReturn("NURSE_INCHARGE");
        when(securityHelper.getCurrentUserId()).thenReturn(20L);
        NurseProfile me = new NurseProfile(); me.setId(11L); me.setHospitalId(7L); me.setIsIncharge(true);
        when(nurseProfileRepository.findByUserId(20L)).thenReturn(Optional.of(me));
        when(wardRepository.findById(3L)).thenReturn(Optional.of(ward(3L, 7L, 99L)));
        assertThatThrownBy(() -> guard.assertWardAccess(3L)).isInstanceOf(AccessDeniedException.class);
    }
}
