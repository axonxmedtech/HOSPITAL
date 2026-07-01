package com.hms.service;

import com.hms.entity.Doctor;
import com.hms.entity.User;
import com.hms.repository.DoctorRepository;
import com.hms.repository.UserRepository;
import com.hms.security.HospitalWebSocketHandler;
import com.hms.security.SecurityContextHelper;
import com.hms.service.AuditLogService;
import com.hms.service.hospital.DoctorService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DoctorServiceTest {

    @Mock DoctorRepository doctorRepository;
    @Mock UserRepository userRepository;
    @Mock PasswordEncoder passwordEncoder;
    @Mock SecurityContextHelper securityHelper;
    @Mock AuditLogService auditLogService;
    @Mock HospitalWebSocketHandler webSocketHandler;

    @InjectMocks DoctorService doctorService;

    @Test
    void addDoctor_savesUserAndLinksDoctor() {
        Long hospitalId = 1L;
        when(securityHelper.getCurrentHospitalId()).thenReturn(hospitalId);
        when(securityHelper.getCurrentUserEmail()).thenReturn("admin@hospital.com");

        Doctor doctor = new Doctor();
        doctor.setName("Dr. Smith");
        doctor.setEmail("smith@hospital.com");
        doctor.setPhone("9876543210");
        doctor.setSpecialization("Cardiology");

        when(doctorRepository.findByEmailAndHospitalId("smith@hospital.com", hospitalId))
                .thenReturn(Optional.empty());
        when(userRepository.existsByEmail("smith@hospital.com")).thenReturn(false);
        when(passwordEncoder.encode("password123")).thenReturn("encoded-password");

        User savedUser = new User();
        savedUser.setId(42L);
        savedUser.setEmail("smith@hospital.com");
        savedUser.setHospitalId(hospitalId);
        when(userRepository.save(any(User.class))).thenReturn(savedUser);

        Doctor savedDoctor = new Doctor();
        savedDoctor.setId(10L);
        savedDoctor.setHospitalId(hospitalId);
        savedDoctor.setUserId(42L);
        when(doctorRepository.save(any(Doctor.class))).thenReturn(savedDoctor);

        Doctor result = doctorService.addDoctor(doctor, "password123");

        assertThat(result).isNotNull();
        assertThat(result.getUserId()).isEqualTo(42L);

        // Verify user saved with correct details
        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        assertThat(userCaptor.getValue().getRole()).isEqualTo("DOCTOR");
        assertThat(userCaptor.getValue().getPassword()).isEqualTo("encoded-password");

        // Verify doctor saved with correct user_id
        ArgumentCaptor<Doctor> doctorCaptor = ArgumentCaptor.forClass(Doctor.class);
        verify(doctorRepository, atLeastOnce()).save(doctorCaptor.capture());
        assertThat(doctorCaptor.getValue().getUserId()).isEqualTo(42L);
    }
}
