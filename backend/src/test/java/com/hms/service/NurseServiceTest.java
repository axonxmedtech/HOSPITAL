package com.hms.service;

import com.hms.entity.Nurse;
import com.hms.entity.User;
import com.hms.repository.AuditLogRepository;
import com.hms.repository.NurseRepository;
import com.hms.repository.NurseWardAssignmentRepository;
import com.hms.repository.UserRepository;
import com.hms.security.HospitalWebSocketHandler;
import com.hms.security.SecurityContextHelper;
import com.hms.service.hospital.NurseService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NurseServiceTest {

    @Mock
    UserRepository userRepository;

    @Mock
    NurseRepository nurseRepository;

    @Mock
    NurseWardAssignmentRepository nurseWardAssignmentRepository;

    @Mock
    PasswordEncoder passwordEncoder;

    @Mock
    SecurityContextHelper securityHelper;

    @Mock
    AuditLogRepository auditLogRepository;

    @Mock
    HospitalWebSocketHandler webSocketHandler;

    @InjectMocks
    NurseService nurseService;

    private void mockSecurityContext() {
        Authentication auth = mock(Authentication.class);
        when(auth.getName()).thenReturn("admin@hospital.com");
        SecurityContext ctx = mock(SecurityContext.class);
        when(ctx.getAuthentication()).thenReturn(auth);
        SecurityContextHolder.setContext(ctx);
    }

    @Test
    void createNurse_savesUserAndNurseProfile() {
        mockSecurityContext();

        Long hospitalId = 1L;
        when(securityHelper.getCurrentHospitalId()).thenReturn(hospitalId);
        when(userRepository.existsByEmail("nurse@test.com")).thenReturn(false);
        when(passwordEncoder.encode("password123")).thenReturn("encoded-password");

        User savedUser = new User();
        savedUser.setId(10L);
        savedUser.setEmail("nurse@test.com");
        savedUser.setRole("NURSE");
        savedUser.setHospitalId(hospitalId);
        when(userRepository.save(any(User.class))).thenReturn(savedUser);

        when(nurseRepository.findMaxNurseSequence(hospitalId)).thenReturn(null);

        Nurse savedNurse = new Nurse();
        savedNurse.setId(1L);
        savedNurse.setUserId(10L);
        when(nurseRepository.save(any(Nurse.class))).thenReturn(savedNurse);

        User result = nurseService.createNurse("Nurse Jane", "nurse@test.com", "password123", "9876543210");

        assertThat(result).isNotNull();
        assertThat(result.getRole()).isEqualTo("NURSE");

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository, atLeastOnce()).save(userCaptor.capture());
        User capturedUser = userCaptor.getAllValues().get(0);
        assertThat(capturedUser.getRole()).isEqualTo("NURSE");
        assertThat(capturedUser.getEmail()).isEqualTo("nurse@test.com");

        ArgumentCaptor<Nurse> nurseCaptor = ArgumentCaptor.forClass(Nurse.class);
        verify(nurseRepository, atLeastOnce()).save(nurseCaptor.capture());
        assertThat(nurseCaptor.getValue().getUserId()).isEqualTo(10L);
    }

    @Test
    void createNurse_throwsWhenEmailExists() {
        when(securityHelper.getCurrentHospitalId()).thenReturn(1L);
        when(userRepository.existsByEmail("duplicate@test.com")).thenReturn(true);

        assertThatThrownBy(() ->
            nurseService.createNurse("Nurse John", "duplicate@test.com", "password123", "1234567890")
        ).isInstanceOf(IllegalArgumentException.class)
         .hasMessageContaining("Email already exists");

        verify(userRepository, never()).save(any());
        verify(nurseRepository, never()).save(any());
    }
}
