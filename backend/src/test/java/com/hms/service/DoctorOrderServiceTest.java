package com.hms.service;

import com.hms.entity.DoctorOrder;
import com.hms.entity.NurseTask;
import com.hms.repository.DoctorOrderRepository;
import com.hms.repository.NurseTaskRepository;
import com.hms.security.SecurityContextHelper;
import com.hms.service.hospital.DoctorOrderService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.Map;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DoctorOrderServiceTest {

    @Mock DoctorOrderRepository orderRepository;
    @Mock NurseTaskRepository taskRepository;
    @Mock SecurityContextHelper securityHelper;

    @InjectMocks DoctorOrderService doctorOrderService;

    @Test
    void createOrder_savesOrderAndCreatesTask() {
        when(securityHelper.getCurrentHospitalId()).thenReturn(1L);
        when(securityHelper.getCurrentUserEmail()).thenReturn("doctor@test.com");

        DoctorOrder saved = new DoctorOrder();
        saved.setId(1L);
        saved.setIpdAdmissionId(5L);
        saved.setHospitalId(1L);
        saved.setStatus("ACTIVE");
        saved.setFrequency("BD");
        when(orderRepository.save(any())).thenReturn(saved);
        when(taskRepository.existsByDoctorOrderIdAndStatus(1L, "PENDING")).thenReturn(false);

        Map<String, Object> data = Map.of(
            "orderType", "MEDICATION",
            "description", "Ceftriaxone 1g IV",
            "frequency", "BD"
        );

        DoctorOrder result = doctorOrderService.createOrder(5L, data);

        assertThat(result.getStatus()).isEqualTo("ACTIVE");
        verify(taskRepository).save(any(NurseTask.class));
    }

    @Test
    void createOrder_doesNotCreateTask_forSosOrders() {
        when(securityHelper.getCurrentHospitalId()).thenReturn(1L);
        when(securityHelper.getCurrentUserEmail()).thenReturn("doctor@test.com");

        DoctorOrder saved = new DoctorOrder();
        saved.setId(1L);
        saved.setIpdAdmissionId(5L);
        saved.setHospitalId(1L);
        saved.setStatus("ACTIVE");
        saved.setFrequency("SOS");
        when(orderRepository.save(any())).thenReturn(saved);

        Map<String, Object> data = Map.of(
            "orderType", "MEDICATION",
            "description", "Paracetamol 500mg",
            "frequency", "SOS"
        );

        doctorOrderService.createOrder(5L, data);

        verify(taskRepository, never()).save(any(NurseTask.class));
    }
}
