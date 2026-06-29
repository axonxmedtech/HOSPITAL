package com.hms.service;

import com.hms.entity.RadiologyTechnician;
import com.hms.entity.User;
import com.hms.repository.AuditLogRepository;
import com.hms.repository.RadiologyTechnicianRepository;
import com.hms.repository.UserRepository;
import com.hms.security.HospitalWebSocketHandler;
import com.hms.security.SecurityContextHelper;
import com.hms.service.hospital.RadiologyTechnicianService;
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
class RadiologyTechnicianServiceTest {

    @Mock UserRepository userRepository;
    @Mock RadiologyTechnicianRepository radiologyTechnicianRepository;
    @Mock PasswordEncoder passwordEncoder;
    @Mock SecurityContextHelper securityHelper;
    @Mock AuditLogRepository auditLogRepository;
    @Mock HospitalWebSocketHandler webSocketHandler;

    @InjectMocks RadiologyTechnicianService radiologyTechnicianService;

    private void mockSecurityContext() {
        Authentication auth = mock(Authentication.class);
        when(auth.getName()).thenReturn("admin@hospital.com");
        SecurityContext ctx = mock(SecurityContext.class);
        when(ctx.getAuthentication()).thenReturn(auth);
        SecurityContextHolder.setContext(ctx);
    }

    @Test
    void create_savesUserAndRadiologyTechnicianProfile() {
        mockSecurityContext();

        Long hospitalId = 1L;
        when(securityHelper.getCurrentHospitalId()).thenReturn(hospitalId);
        when(userRepository.existsByEmail("rad@test.com")).thenReturn(false);
        when(passwordEncoder.encode("password123")).thenReturn("encoded-password");

        User savedUser = new User();
        savedUser.setId(10L);
        savedUser.setEmail("rad@test.com");
        savedUser.setRole("RADIOLOGY_TECHNICIAN");
        savedUser.setHospitalId(hospitalId);
        when(userRepository.save(any(User.class))).thenReturn(savedUser);

        when(radiologyTechnicianRepository.findMaxRadiologyTechSequence(hospitalId)).thenReturn(null);

        RadiologyTechnician savedProfile = new RadiologyTechnician();
        savedProfile.setId(1L);
        when(radiologyTechnicianRepository.save(any(RadiologyTechnician.class))).thenReturn(savedProfile);

        User result = radiologyTechnicianService.create("Rad Tech Jane", "rad@test.com", "password123", "9876543210");

        assertThat(result).isNotNull();
        assertThat(result.getRole()).isEqualTo("RADIOLOGY_TECHNICIAN");

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository, atLeastOnce()).save(userCaptor.capture());
        User capturedUser = userCaptor.getAllValues().get(0);
        assertThat(capturedUser.getRole()).isEqualTo("RADIOLOGY_TECHNICIAN");
        assertThat(capturedUser.getEmail()).isEqualTo("rad@test.com");

        verify(radiologyTechnicianRepository, atLeastOnce()).save(any(RadiologyTechnician.class));
    }

    @Test
    void create_throwsWhenEmailExists() {
        when(securityHelper.getCurrentHospitalId()).thenReturn(1L);
        when(userRepository.existsByEmail("duplicate@test.com")).thenReturn(true);

        assertThatThrownBy(() -> radiologyTechnicianService.create("Name", "duplicate@test.com", "password", "123"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Email already registered");
    }
}
