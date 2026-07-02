package com.hms.service;

import com.hms.dto.LabResultRequest;
import com.hms.entity.Doctor;
import com.hms.entity.LabOrder;
import com.hms.entity.LabResult;
import com.hms.exception.UnauthorizedException;
import com.hms.repository.*;
import com.hms.security.HospitalWebSocketHandler;
import com.hms.security.SecurityContextHelper;
import com.hms.service.hospital.LabWorkflowService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LabWorkflowServiceTest {

    @Mock private LabOrderRepository labOrderRepository;
    @Mock private LabResultRepository labResultRepository;
    @Mock private AuditLogRepository auditLogRepository;
    @Mock private SecurityContextHelper securityHelper;
    @Mock private com.hms.service.hospital.MrdService mrdService;
    @Mock private HospitalWebSocketHandler webSocketHandler;
    @Mock private com.hms.service.hospital.BillingService billingService;
    @Mock private DoctorRepository doctorRepository;

    @InjectMocks
    private LabWorkflowService service;

    private void stubAuditActor() {
        Authentication authentication = mock(Authentication.class);
        lenient().when(authentication.getName()).thenReturn("someone@hospital.com");
        SecurityContext securityContext = mock(SecurityContext.class);
        lenient().when(securityContext.getAuthentication()).thenReturn(authentication);
        SecurityContextHolder.setContext(securityContext);
    }

    private LabOrder completedOrder(Long hospitalId) {
        LabOrder order = new LabOrder();
        order.setId(1L);
        order.setPublicId("order-uuid");
        order.setHospitalId(hospitalId);
        order.setTestName("CBC");
        order.setStatus("COMPLETED");
        return order;
    }

    @Test
    void verifyResult_rejectsNonPathologistDoctor() {
        Long hospitalId = 1L;
        stubAuditActor();
        when(securityHelper.getCurrentHospitalId()).thenReturn(hospitalId);
        when(securityHelper.getCurrentUserEmail()).thenReturn("doc@hospital.com");
        Doctor notPathologist = new Doctor();
        notPathologist.setName("Dr. Smith");
        notPathologist.setIsPathologist(false);
        when(doctorRepository.findByEmailAndHospitalId("doc@hospital.com", hospitalId))
                .thenReturn(Optional.of(notPathologist));

        assertThatThrownBy(() -> service.verifyResult("order-uuid"))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessageContaining("pathologist");
        verify(labResultRepository, never()).save(any());
    }

    @Test
    void verifyResult_rejectedWhenOrderNotCompleted() {
        Long hospitalId = 1L;
        stubAuditActor();
        when(securityHelper.getCurrentHospitalId()).thenReturn(hospitalId);
        when(securityHelper.getCurrentUserEmail()).thenReturn("path@hospital.com");
        Doctor pathologist = new Doctor();
        pathologist.setName("Dr. Path");
        pathologist.setIsPathologist(true);
        when(doctorRepository.findByEmailAndHospitalId("path@hospital.com", hospitalId))
                .thenReturn(Optional.of(pathologist));
        LabOrder order = completedOrder(hospitalId);
        order.setStatus("SAMPLE_COLLECTED"); // result not entered yet
        when(labOrderRepository.findByPublicIdAndHospitalId("order-uuid", hospitalId))
                .thenReturn(Optional.of(order));

        assertThatThrownBy(() -> service.verifyResult("order-uuid"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("COMPLETED");
    }

    @Test
    void verifyResult_pathologistSignsOffSuccessfully() {
        Long hospitalId = 1L;
        stubAuditActor();
        when(securityHelper.getCurrentHospitalId()).thenReturn(hospitalId);
        when(securityHelper.getCurrentUserEmail()).thenReturn("path@hospital.com");
        Doctor pathologist = new Doctor();
        pathologist.setName("Dr. Path");
        pathologist.setIsPathologist(true);
        when(doctorRepository.findByEmailAndHospitalId("path@hospital.com", hospitalId))
                .thenReturn(Optional.of(pathologist));
        LabOrder order = completedOrder(hospitalId);
        when(labOrderRepository.findByPublicIdAndHospitalId("order-uuid", hospitalId))
                .thenReturn(Optional.of(order));
        LabResult result = new LabResult();
        result.setLabOrderId(1L);
        when(labResultRepository.findByLabOrderId(1L)).thenReturn(Optional.of(result));
        when(labResultRepository.save(any(LabResult.class))).thenAnswer(inv -> inv.getArgument(0));
        when(labOrderRepository.save(any(LabOrder.class))).thenAnswer(inv -> inv.getArgument(0));

        Map<String, Object> dto = service.verifyResult("order-uuid");

        LabOrder savedOrder = (LabOrder) dto.get("order");
        LabResult savedResult = (LabResult) dto.get("result");
        assertThat(savedOrder.getStatus()).isEqualTo("VERIFIED");
        assertThat(savedResult.getVerifiedByName()).isEqualTo("Dr. Path");
        assertThat(savedResult.getVerifiedAt()).isNotNull();
    }

    @Test
    void releaseResult_rejectedBeforeVerification() {
        Long hospitalId = 1L;
        stubAuditActor();
        when(securityHelper.getCurrentHospitalId()).thenReturn(hospitalId);
        LabOrder order = completedOrder(hospitalId); // status COMPLETED, not VERIFIED
        when(labOrderRepository.findByPublicIdAndHospitalId("order-uuid", hospitalId))
                .thenReturn(Optional.of(order));

        assertThatThrownBy(() -> service.releaseResult("order-uuid"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("VERIFIED");
    }

    @Test
    void releaseResult_succeedsWhenVerified() {
        Long hospitalId = 1L;
        stubAuditActor();
        when(securityHelper.getCurrentHospitalId()).thenReturn(hospitalId);
        when(securityHelper.getCurrentUserEmail()).thenReturn("nurse@hospital.com");
        LabOrder order = completedOrder(hospitalId);
        order.setStatus("VERIFIED");
        when(labOrderRepository.findByPublicIdAndHospitalId("order-uuid", hospitalId))
                .thenReturn(Optional.of(order));
        LabResult result = new LabResult();
        when(labResultRepository.findByLabOrderId(1L)).thenReturn(Optional.of(result));
        when(labResultRepository.save(any(LabResult.class))).thenAnswer(inv -> inv.getArgument(0));
        when(labOrderRepository.save(any(LabOrder.class))).thenAnswer(inv -> inv.getArgument(0));

        Map<String, Object> dto = service.releaseResult("order-uuid");

        assertThat(((LabOrder) dto.get("order")).getStatus()).isEqualTo("RELEASED");
        assertThat(((LabResult) dto.get("result")).getReleasedAt()).isNotNull();
    }

    @Test
    void enterResult_criticalFlagTriggersAlertAndDoesNotSelfAttestVerification() {
        Long hospitalId = 1L;
        stubAuditActor();
        when(securityHelper.getCurrentHospitalId()).thenReturn(hospitalId);
        when(securityHelper.getCurrentUserEmail()).thenReturn("tech@hospital.com");
        LabOrder order = completedOrder(hospitalId);
        order.setStatus("SAMPLE_COLLECTED");
        when(labOrderRepository.findByPublicIdAndHospitalId("order-uuid", hospitalId))
                .thenReturn(Optional.of(order));
        when(labResultRepository.existsByLabOrderId(1L)).thenReturn(false);
        when(labResultRepository.save(any(LabResult.class))).thenAnswer(inv -> inv.getArgument(0));
        when(labOrderRepository.save(any(LabOrder.class))).thenAnswer(inv -> inv.getArgument(0));

        LabResultRequest req = new LabResultRequest();
        req.setParameters("[{\"name\":\"K+\",\"value\":\"7.2\"}]");
        req.setIsAbnormal(true);
        req.setIsCritical(true);

        Map<String, Object> dto = service.enterResult("order-uuid", req);

        LabResult savedResult = (LabResult) dto.get("result");
        assertThat(savedResult.getIsCritical()).isTrue();
        assertThat(savedResult.getVerifiedByName()).isNull(); // not self-attested
        verify(webSocketHandler, atLeastOnce()).broadcast(eq(hospitalId), contains("CRITICAL_LAB_ALERT"));
    }

    @Test
    void cancelOrder_blockedOnceVerified() {
        Long hospitalId = 1L;
        when(securityHelper.getCurrentHospitalId()).thenReturn(hospitalId);
        LabOrder order = completedOrder(hospitalId);
        order.setStatus("VERIFIED");
        when(labOrderRepository.findByPublicIdAndHospitalId("order-uuid", hospitalId))
                .thenReturn(Optional.of(order));

        assertThatThrownBy(() -> service.cancelOrder("order-uuid"))
                .isInstanceOf(IllegalStateException.class);
    }
}
