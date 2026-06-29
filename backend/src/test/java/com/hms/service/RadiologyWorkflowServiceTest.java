package com.hms.service;

import com.hms.dto.RadiologyOrderRequest;
import com.hms.dto.RadiologyResultRequest;
import com.hms.entity.*;
import com.hms.repository.*;
import com.hms.security.*;
import com.hms.service.hospital.RadiologyWorkflowService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RadiologyWorkflowServiceTest {

    @Mock RadiologyOrderRepository radiologyOrderRepository;
    @Mock RadiologyResultRepository radiologyResultRepository;
    @Mock AuditLogRepository auditLogRepository;
    @Mock SecurityContextHelper securityHelper;
    @Mock HospitalWebSocketHandler webSocketHandler;

    @InjectMocks RadiologyWorkflowService radiologyWorkflowService;

    private void mockSecurityContext() {
        Authentication auth = mock(Authentication.class);
        when(auth.getName()).thenReturn("doctor@hospital.com");
        SecurityContext ctx = mock(SecurityContext.class);
        when(ctx.getAuthentication()).thenReturn(auth);
        SecurityContextHolder.setContext(ctx);
    }

    @Test
    void placeOrder_savesOrderAndBroadcasts() {
        mockSecurityContext();
        Long hospitalId = 1L;
        when(securityHelper.getCurrentHospitalId()).thenReturn(hospitalId);
        when(securityHelper.getCurrentUserEmail()).thenReturn("doctor@hospital.com");

        RadiologyOrderRequest req = new RadiologyOrderRequest();
        req.setTestName("Chest X-Ray");
        req.setPatientId(45L);
        req.setIpdAdmissionId(12L);
        req.setNotes("r/o pneumonia");

        RadiologyOrder savedOrder = new RadiologyOrder();
        savedOrder.setId(100L);
        savedOrder.setStatus("ORDERED");
        when(radiologyOrderRepository.save(any(RadiologyOrder.class))).thenReturn(savedOrder);

        RadiologyOrder result = radiologyWorkflowService.placeOrder(req);

        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(100L);
        verify(radiologyOrderRepository).save(any(RadiologyOrder.class));
        verify(webSocketHandler).broadcast(eq(hospitalId), anyString());
    }

    @Test
    void conductStudy_transitionsStatus() {
        mockSecurityContext();
        Long hospitalId = 1L;
        when(securityHelper.getCurrentHospitalId()).thenReturn(hospitalId);
        when(securityHelper.getCurrentUserEmail()).thenReturn("tech@hospital.com");

        RadiologyOrder order = new RadiologyOrder();
        order.setId(100L);
        order.setHospitalId(hospitalId);
        order.setStatus("ORDERED");

        when(radiologyOrderRepository.findByPublicIdAndHospitalId("order-uuid", hospitalId))
                .thenReturn(Optional.of(order));
        when(radiologyOrderRepository.save(any(RadiologyOrder.class))).thenAnswer(i -> i.getArgument(0));

        RadiologyOrder updated = radiologyWorkflowService.conductStudy("order-uuid");

        assertThat(updated.getStatus()).isEqualTo("STUDY_CONDUCTED");
        assertThat(updated.getStudyConductedByName()).isEqualTo("tech@hospital.com");
        assertThat(updated.getStudyConductedAt()).isNotNull();
        verify(webSocketHandler).broadcast(eq(hospitalId), anyString());
    }

    @Test
    void enterResult_savesResultAndCompletesOrder() {
        mockSecurityContext();
        Long hospitalId = 1L;
        when(securityHelper.getCurrentHospitalId()).thenReturn(hospitalId);
        when(securityHelper.getCurrentUserEmail()).thenReturn("tech@hospital.com");

        RadiologyOrder order = new RadiologyOrder();
        order.setId(100L);
        order.setHospitalId(hospitalId);
        order.setStatus("STUDY_CONDUCTED");

        when(radiologyOrderRepository.findByPublicIdAndHospitalId("order-uuid", hospitalId))
                .thenReturn(Optional.of(order));
        when(radiologyResultRepository.existsByRadiologyOrderId(100L)).thenReturn(false);

        RadiologyResultRequest req = new RadiologyResultRequest();
        req.setFindings("Lungs are clear");
        req.setImpression("No acute cardiopulmonary disease");
        req.setIsAbnormal(false);

        when(radiologyResultRepository.save(any(RadiologyResult.class))).thenAnswer(i -> i.getArgument(0));
        when(radiologyOrderRepository.save(any(RadiologyOrder.class))).thenAnswer(i -> i.getArgument(0));

        Map<String, Object> resultDto = radiologyWorkflowService.enterResult("order-uuid", req);

        RadiologyOrder finalOrder = (RadiologyOrder) resultDto.get("order");
        RadiologyResult finalResult = (RadiologyResult) resultDto.get("result");

        assertThat(finalOrder.getStatus()).isEqualTo("COMPLETED");
        assertThat(finalResult.getFindings()).isEqualTo("Lungs are clear");
        assertThat(finalResult.getImpression()).isEqualTo("No acute cardiopulmonary disease");
        verify(webSocketHandler).broadcast(eq(hospitalId), anyString());
    }
}
