package com.hms.service.hospital;

import com.hms.entity.NurseProfile;
import com.hms.entity.User;
import com.hms.repository.AuditLogRepository;
import com.hms.repository.NurseProfileRepository;
import com.hms.repository.UserRepository;
import com.hms.exception.UnauthorizedException;
import com.hms.security.HospitalWebSocketHandler;
import com.hms.security.SecurityContextHelper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NurseServiceTest {

    @Mock UserRepository userRepository;
    @Mock NurseProfileRepository nurseProfileRepository;
    @Mock PasswordEncoder passwordEncoder;
    @Mock SecurityContextHelper securityHelper;
    @Mock AuditLogRepository auditLogRepository;
    @Mock HospitalWebSocketHandler webSocketHandler;
    @Mock com.hms.repository.WardRepository wardRepository;
    @Mock com.hms.service.AuditLogService auditLogService;

    @InjectMocks NurseService nurseService;

    @Test
    void createNurse_setsRoleAndSequentialCustomId_andCreatesProfile() {
        when(securityHelper.getCurrentHospitalId()).thenReturn(7L);
        when(userRepository.existsByEmail("nina@h.com")).thenReturn(false);
        when(passwordEncoder.encode("secret1")).thenReturn("ENC");
        when(userRepository.findMaxNurseSequence()).thenReturn(4);
        when(userRepository.save(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            if (u.getId() == null) u.setId(101L);
            return u;
        });

        User result = nurseService.createNurse("Nina", "nina@h.com", "secret1", "999", "LIC-1", null);

        assertThat(result.getRole()).isEqualTo("NURSE");
        assertThat(result.getHospitalId()).isEqualTo(7L);
        assertThat(result.getCustomId()).isEqualTo("NRS5"); // max 4 + 1
        assertThat(result.getPassword()).isEqualTo("ENC");

        ArgumentCaptor<NurseProfile> profile = ArgumentCaptor.forClass(NurseProfile.class);
        verify(nurseProfileRepository).save(profile.capture());
        assertThat(profile.getValue().getUserId()).isEqualTo(101L);
        assertThat(profile.getValue().getHospitalId()).isEqualTo(7L);
        assertThat(profile.getValue().getLicenseNumber()).isEqualTo("LIC-1");
        assertThat(profile.getValue().getCustomId()).isEqualTo("NRS5");
    }

    @Test
    void createNurse_firstNurse_startsAtOne() {
        when(securityHelper.getCurrentHospitalId()).thenReturn(7L);
        when(userRepository.existsByEmail(any())).thenReturn(false);
        when(passwordEncoder.encode(any())).thenReturn("ENC");
        when(userRepository.findMaxNurseSequence()).thenReturn(null); // no nurses yet
        when(userRepository.save(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            if (u.getId() == null) u.setId(1L);
            return u;
        });

        User result = nurseService.createNurse("A", "a@h.com", "secret1", null, null, null);

        assertThat(result.getCustomId()).isEqualTo("NRS1");
    }

    @Test
    void createNurse_rejectsDuplicateEmail() {
        when(securityHelper.getCurrentHospitalId()).thenReturn(7L);
        when(userRepository.existsByEmail("dup@h.com")).thenReturn(true);

        assertThatThrownBy(() -> nurseService.createNurse("D", "dup@h.com", "secret1", null, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Email already exists");
        verify(userRepository, never()).save(any());
    }

    @Test
    void createNurse_rejectsMissingHospitalContext() {
        when(securityHelper.getCurrentHospitalId()).thenReturn(null);

        assertThatThrownBy(() -> nurseService.createNurse("D", "d@h.com", "secret1", null, null, null))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void getNurseByPublicId_deniesCrossHospitalAccess() {
        when(securityHelper.getCurrentHospitalId()).thenReturn(7L);
        User other = new User();
        other.setRole("NURSE");
        other.setHospitalId(99L); // different hospital
        when(userRepository.findByPublicId("pub-1")).thenReturn(Optional.of(other));

        assertThatThrownBy(() -> nurseService.getNurseByPublicId("pub-1"))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessageContaining("another hospital");
    }

    @Test
    void getNurseByPublicId_rejectsNonNurseTarget() {
        when(securityHelper.getCurrentHospitalId()).thenReturn(7L);
        User doctor = new User();
        doctor.setRole("DOCTOR");
        doctor.setHospitalId(7L);
        when(userRepository.findByPublicId("pub-2")).thenReturn(Optional.of(doctor));

        assertThatThrownBy(() -> nurseService.getNurseByPublicId("pub-2"))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessageContaining("not a nurse");
    }

    @Test
    void deleteNurse_softDeletesUserAndProfile() {
        when(securityHelper.getCurrentHospitalId()).thenReturn(7L);
        User nurse = new User();
        nurse.setId(55L);
        nurse.setRole("NURSE");
        nurse.setHospitalId(7L);
        nurse.setIsActive(true);
        when(userRepository.findByPublicId("pub-3")).thenReturn(Optional.of(nurse));
        NurseProfile profile = new NurseProfile();
        profile.setIsActive(true);
        when(nurseProfileRepository.findByUserId(55L)).thenReturn(Optional.of(profile));

        nurseService.deleteNurse("pub-3", "left the hospital");

        assertThat(nurse.getIsActive()).isFalse();
        assertThat(profile.getIsActive()).isFalse();
        verify(userRepository).save(nurse);
        verify(nurseProfileRepository).save(profile);
    }

    @Test
    void demote_blockedWhileHoldingWards() {
        when(securityHelper.getCurrentHospitalId()).thenReturn(7L);
        NurseProfile p = new NurseProfile();
        p.setId(11L); p.setHospitalId(7L); p.setIsIncharge(true);
        when(nurseProfileRepository.findById(11L)).thenReturn(Optional.of(p));
        when(wardRepository.findByHospitalIdAndInchargeNurseId(7L, 11L))
                .thenReturn(java.util.List.of(new com.hms.entity.Ward()));

        assertThatThrownBy(() -> nurseService.demote(11L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ward");
    }

    @Test
    void promote_setsInchargeFlag() {
        when(securityHelper.getCurrentHospitalId()).thenReturn(7L);
        NurseProfile p = new NurseProfile();
        p.setId(11L); p.setHospitalId(7L); p.setIsIncharge(false); p.setEmail("n@x.com"); p.setUserId(20L);
        when(nurseProfileRepository.findById(11L)).thenReturn(Optional.of(p));
        User u = new User(); u.setId(20L); u.setRole("NURSE");
        when(userRepository.findById(20L)).thenReturn(Optional.of(u));
        when(nurseProfileRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(userRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        nurseService.promote(11L);

        assertThat(p.getIsIncharge()).isTrue();
        assertThat(u.getRole()).isEqualTo("NURSE_INCHARGE");
    }
}
