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
    @Mock DoctorRepository doctorRepository;

    @InjectMocks RadiologyWorkflowService radiologyWorkflowService;

    private void mockSecurityContext() {
        Authentication auth = mock(Authentication.class);
        lenient().when(auth.getName()).thenReturn("doctor@hospital.com");
        SecurityContext ctx = mock(SecurityContext.class);
        lenient().when(ctx.getAuthentication()).thenReturn(auth);
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

    // ===== Radiologist sign-off gate (Form 28 BR-4/5/6) =====

    private RadiologyOrder completedOrder(Long hospitalId) {
        RadiologyOrder order = new RadiologyOrder();
        order.setId(100L);
        order.setHospitalId(hospitalId);
        order.setTestName("Chest X-Ray");
        order.setStatus("COMPLETED");
        return order;
    }

    @Test
    void verifyResult_rejectsNonRadiologistDoctor() {
        mockSecurityContext();
        Long hospitalId = 1L;
        when(securityHelper.getCurrentHospitalId()).thenReturn(hospitalId);
        when(securityHelper.getCurrentUserEmail()).thenReturn("doc@hospital.com");
        Doctor notRadiologist = new Doctor();
        notRadiologist.setName("Dr. Smith");
        notRadiologist.setIsRadiologist(false);
        when(doctorRepository.findByEmailAndHospitalId("doc@hospital.com", hospitalId))
                .thenReturn(Optional.of(notRadiologist));

        assertThatThrownBy(() -> radiologyWorkflowService.verifyResult("order-uuid"))
                .isInstanceOf(com.hms.exception.UnauthorizedException.class)
                .hasMessageContaining("radiologist");
        verify(radiologyResultRepository, never()).save(any());
    }

    @Test
    void verifyResult_radiologistSignsOffSuccessfully() {
        mockSecurityContext();
        Long hospitalId = 1L;
        when(securityHelper.getCurrentHospitalId()).thenReturn(hospitalId);
        when(securityHelper.getCurrentUserEmail()).thenReturn("rad@hospital.com");
        Doctor radiologist = new Doctor();
        radiologist.setName("Dr. Rad");
        radiologist.setIsRadiologist(true);
        when(doctorRepository.findByEmailAndHospitalId("rad@hospital.com", hospitalId))
                .thenReturn(Optional.of(radiologist));
        RadiologyOrder order = completedOrder(hospitalId);
        when(radiologyOrderRepository.findByPublicIdAndHospitalId("order-uuid", hospitalId))
                .thenReturn(Optional.of(order));
        RadiologyResult result = new RadiologyResult();
        result.setRadiologyOrderId(100L);
        when(radiologyResultRepository.findByRadiologyOrderId(100L)).thenReturn(Optional.of(result));
        when(radiologyResultRepository.save(any(RadiologyResult.class))).thenAnswer(i -> i.getArgument(0));
        when(radiologyOrderRepository.save(any(RadiologyOrder.class))).thenAnswer(i -> i.getArgument(0));

        Map<String, Object> dto = radiologyWorkflowService.verifyResult("order-uuid");

        assertThat(((RadiologyOrder) dto.get("order")).getStatus()).isEqualTo("VERIFIED");
        assertThat(((RadiologyResult) dto.get("result")).getVerifiedByName()).isEqualTo("Dr. Rad");
        assertThat(((RadiologyResult) dto.get("result")).getVerifiedAt()).isNotNull();
    }

    @Test
    void releaseResult_rejectedBeforeVerification() {
        Long hospitalId = 1L;
        when(securityHelper.getCurrentHospitalId()).thenReturn(hospitalId);
        RadiologyOrder order = completedOrder(hospitalId);
        when(radiologyOrderRepository.findByPublicIdAndHospitalId("order-uuid", hospitalId))
                .thenReturn(Optional.of(order));

        assertThatThrownBy(() -> radiologyWorkflowService.releaseResult("order-uuid"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("VERIFIED");
    }

    @Test
    void enterResult_criticalFlagFiresAlertWithoutSelfAttestation() {
        mockSecurityContext();
        Long hospitalId = 1L;
        when(securityHelper.getCurrentHospitalId()).thenReturn(hospitalId);
        when(securityHelper.getCurrentUserEmail()).thenReturn("tech@hospital.com");
        RadiologyOrder order = new RadiologyOrder();
        order.setId(100L);
        order.setHospitalId(hospitalId);
        order.setTestName("CT Head");
        order.setStatus("STUDY_CONDUCTED");
        when(radiologyOrderRepository.findByPublicIdAndHospitalId("order-uuid", hospitalId))
                .thenReturn(Optional.of(order));
        when(radiologyResultRepository.existsByRadiologyOrderId(100L)).thenReturn(false);
        when(radiologyResultRepository.save(any(RadiologyResult.class))).thenAnswer(i -> i.getArgument(0));
        when(radiologyOrderRepository.save(any(RadiologyOrder.class))).thenAnswer(i -> i.getArgument(0));

        RadiologyResultRequest req = new RadiologyResultRequest();
        req.setFindings("Acute intracranial hemorrhage");
        req.setIsAbnormal(true);
        req.setIsCritical(true);

        Map<String, Object> dto = radiologyWorkflowService.enterResult("order-uuid", req);

        RadiologyResult savedResult = (RadiologyResult) dto.get("result");
        assertThat(savedResult.getIsCritical()).isTrue();
        assertThat(savedResult.getVerifiedByName()).isNull();
        verify(webSocketHandler, atLeastOnce()).broadcast(eq(hospitalId), contains("CRITICAL_RADIOLOGY_ALERT"));
    }

    @Test
    void cancelOrder_blockedOnceVerified() {
        Long hospitalId = 1L;
        when(securityHelper.getCurrentHospitalId()).thenReturn(hospitalId);
        RadiologyOrder order = completedOrder(hospitalId);
        order.setStatus("VERIFIED");
        when(radiologyOrderRepository.findByPublicIdAndHospitalId("order-uuid", hospitalId))
                .thenReturn(Optional.of(order));

        assertThatThrownBy(() -> radiologyWorkflowService.cancelOrder("order-uuid"))
                .isInstanceOf(IllegalStateException.class);
    }
}
