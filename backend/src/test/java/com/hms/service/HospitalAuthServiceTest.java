package com.hms.service;

import com.hms.dto.HospitalSettingDTO;
import com.hms.entity.Hospital;
import com.hms.entity.HospitalSetting;
import com.hms.entity.User;
import com.hms.repository.*;
import com.hms.security.JwtUtil;
import com.hms.service.hospital.HospitalAuthService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class HospitalAuthServiceTest {

    @Mock UserRepository userRepository;
    @Mock HospitalRepository hospitalRepository;
    @Mock HospitalSettingRepository hospitalSettingRepository;
    @Mock PasswordEncoder passwordEncoder;
    @Mock JwtUtil jwtUtil;
    @Mock HospitalAdminRepository hospitalAdminRepository;
    @Mock ReceptionistProfileRepository receptionistProfileRepository;
    @Mock PharmacistProfileRepository pharmacistProfileRepository;
    @Mock DoctorRepository doctorRepository;

    @InjectMocks HospitalAuthService service;

    private User adminUser;
    private HospitalSetting existingSetting;

    @BeforeEach
    void setUp() {
        adminUser = new User();
        adminUser.setEmail("admin@test.com");
        adminUser.setRole("HOSPITAL_ADMIN");
        adminUser.setHospitalId(1L);

        Hospital h = new Hospital();
        h.setId(1L);

        existingSetting = new HospitalSetting();
        existingSetting.setHospital(h);
        existingSetting.setReceptionMode("HAS_RECEPTIONIST");
        existingSetting.setBillingHandler("RECEPTIONIST");
        existingSetting.setInClinic(true);
    }

    @Test
    void updateSettings_soloMode_forcesBillingHandlerToDoctor() {
        Hospital h = new Hospital();
        h.setId(1L);
        h.setModules(java.util.List.of("IN_CLINIC"));
        when(hospitalRepository.findById(1L)).thenReturn(Optional.of(h));
        when(userRepository.findByEmail("admin@test.com")).thenReturn(Optional.of(adminUser));
        when(hospitalSettingRepository.findByHospital_Id(1L)).thenReturn(Optional.of(existingSetting));

        HospitalSettingDTO dto = new HospitalSettingDTO("SOLO", "RECEPTIONIST", true, "FIXED");
        HospitalSettingDTO result = service.updateHospitalOperationsSettings("admin@test.com", dto);

        assertThat(result.getBillingHandler()).isEqualTo("DOCTOR");
        verify(hospitalSettingRepository).updateByHospitalId(1L, "SOLO", "DOCTOR", true, "FIXED");
    }

    @Test
    void updateSettings_nullInClinic_preservesExistingValue() {
        Hospital h = new Hospital();
        h.setId(1L);
        // No IN_CLINIC module — null DTO value should still preserve the existing setting
        when(hospitalRepository.findById(1L)).thenReturn(Optional.of(h));
        when(userRepository.findByEmail("admin@test.com")).thenReturn(Optional.of(adminUser));
        when(hospitalSettingRepository.findByHospital_Id(1L)).thenReturn(Optional.of(existingSetting));

        HospitalSettingDTO dto = new HospitalSettingDTO("HAS_RECEPTIONIST", "RECEPTIONIST", null, "FIXED");
        HospitalSettingDTO result = service.updateHospitalOperationsSettings("admin@test.com", dto);

        // inClinic was null in DTO → should be preserved from existingSetting (true)
        assertThat(result.getInClinic()).isTrue();
        verify(hospitalSettingRepository).updateByHospitalId(1L, "HAS_RECEPTIONIST", "RECEPTIONIST", true, "FIXED");
    }

    @Test
    void updateSettings_invalidReceptionMode_throws() {
        when(userRepository.findByEmail("admin@test.com")).thenReturn(Optional.of(adminUser));

        HospitalSettingDTO dto = new HospitalSettingDTO("INVALID_MODE", "RECEPTIONIST", true, "FIXED");
        assertThatThrownBy(() -> service.updateHospitalOperationsSettings("admin@test.com", dto))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("receptionMode");
    }

    @Test
    void updateSettings_invalidBillingHandler_throws() {
        when(userRepository.findByEmail("admin@test.com")).thenReturn(Optional.of(adminUser));

        HospitalSettingDTO dto = new HospitalSettingDTO("HAS_RECEPTIONIST", "INVALID_HANDLER", true, "FIXED");
        assertThatThrownBy(() -> service.updateHospitalOperationsSettings("admin@test.com", dto))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("billingHandler");
    }

    @Test
    void updateSettings_nonAdminRole_throws() {
        User doctorUser = new User();
        doctorUser.setEmail("doc@test.com");
        doctorUser.setRole("DOCTOR");
        doctorUser.setHospitalId(1L);
        when(userRepository.findByEmail("doc@test.com")).thenReturn(Optional.of(doctorUser));

        HospitalSettingDTO dto = new HospitalSettingDTO("HAS_RECEPTIONIST", "RECEPTIONIST", true, "FIXED");
        assertThatThrownBy(() -> service.updateHospitalOperationsSettings("doc@test.com", dto))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Access denied");
    }
}
