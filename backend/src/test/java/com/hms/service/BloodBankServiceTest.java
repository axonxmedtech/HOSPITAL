package com.hms.service;

import com.hms.dto.*;
import com.hms.entity.*;
import com.hms.repository.*;
import com.hms.security.HospitalWebSocketHandler;
import com.hms.security.SecurityContextHelper;
import com.hms.service.hospital.BloodBankService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BloodBankServiceTest {

    @Mock private BloodDonorRepository donorRepository;
    @Mock private BloodUnitRepository unitRepository;
    @Mock private BloodRequestRepository requestRepository;
    @Mock private CrossMatchRepository crossMatchRepository;
    @Mock private TransfusionRecordRepository transfusionRepository;
    @Mock private SecurityContextHelper securityHelper;
    @Mock private AuditLogRepository auditLogRepository;
    @Mock private HospitalWebSocketHandler webSocketHandler;

    @InjectMocks
    private BloodBankService service;

    private void stubActor() {
        Authentication authentication = mock(Authentication.class);
        lenient().when(authentication.getName()).thenReturn("officer@hospital.com");
        SecurityContext ctx = mock(SecurityContext.class);
        lenient().when(ctx.getAuthentication()).thenReturn(authentication);
        SecurityContextHolder.setContext(ctx);
    }

    private BloodUnit availableUnit(Long hospitalId) {
        BloodUnit unit = new BloodUnit();
        unit.setId(1L);
        unit.setHospitalId(hospitalId);
        unit.setUnitNumber("BAG-1-1000");
        unit.setDonorId(10L);
        unit.setStatus("AVAILABLE");
        unit.setExpiryDate(LocalDate.now().plusDays(20));
        return unit;
    }

    // ===== BR-3 expiry / screening gate =====

    @Test
    void addUnit_quarantinesReactiveScreening() {
        Long hospitalId = 1L;
        stubActor();
        when(securityHelper.getCurrentHospitalId()).thenReturn(hospitalId);
        BloodDonor donor = new BloodDonor();
        donor.setId(10L);
        donor.setHospitalId(hospitalId);
        when(donorRepository.findByIdAndHospitalId(10L, hospitalId)).thenReturn(Optional.of(donor));
        when(unitRepository.save(any(BloodUnit.class))).thenAnswer(i -> i.getArgument(0));

        BloodUnitRequest req = new BloodUnitRequest();
        req.setDonorId(10L);
        req.setComponentType("PRBC");
        req.setBloodGroup("O");
        req.setRhType("POSITIVE");
        req.setHivResult("REACTIVE");
        req.setExpiryDate(LocalDate.now().plusDays(30));

        BloodUnit saved = service.addUnit(req);

        assertThat(saved.getStatus()).isEqualTo("QUARANTINED");
    }

    @Test
    void addUnit_quarantinesAlreadyExpired() {
        Long hospitalId = 1L;
        stubActor();
        when(securityHelper.getCurrentHospitalId()).thenReturn(hospitalId);
        BloodDonor donor = new BloodDonor();
        donor.setId(10L);
        donor.setHospitalId(hospitalId);
        when(donorRepository.findByIdAndHospitalId(10L, hospitalId)).thenReturn(Optional.of(donor));
        when(unitRepository.save(any(BloodUnit.class))).thenAnswer(i -> i.getArgument(0));

        BloodUnitRequest req = new BloodUnitRequest();
        req.setDonorId(10L);
        req.setComponentType("PRBC");
        req.setBloodGroup("O");
        req.setRhType("POSITIVE");
        req.setExpiryDate(LocalDate.now().minusDays(1));

        BloodUnit saved = service.addUnit(req);

        assertThat(saved.getStatus()).isEqualTo("QUARANTINED");
    }

    @Test
    void addUnit_cleanScreeningAndFutureExpiry_isAvailable() {
        Long hospitalId = 1L;
        stubActor();
        when(securityHelper.getCurrentHospitalId()).thenReturn(hospitalId);
        BloodDonor donor = new BloodDonor();
        donor.setId(10L);
        donor.setHospitalId(hospitalId);
        when(donorRepository.findByIdAndHospitalId(10L, hospitalId)).thenReturn(Optional.of(donor));
        when(unitRepository.save(any(BloodUnit.class))).thenAnswer(i -> i.getArgument(0));

        BloodUnitRequest req = new BloodUnitRequest();
        req.setDonorId(10L);
        req.setComponentType("PRBC");
        req.setBloodGroup("O");
        req.setRhType("POSITIVE");
        req.setHivResult("NON_REACTIVE");
        req.setExpiryDate(LocalDate.now().plusDays(30));

        BloodUnit saved = service.addUnit(req);

        assertThat(saved.getStatus()).isEqualTo("AVAILABLE");
    }

    // ===== BR-4 cross-match gate on issue =====

    @Test
    void issueUnit_rejectedWithoutCrossMatch() {
        Long hospitalId = 1L;
        when(securityHelper.getCurrentHospitalId()).thenReturn(hospitalId);
        BloodUnit unit = availableUnit(hospitalId);
        when(unitRepository.findByIdAndHospitalId(1L, hospitalId)).thenReturn(Optional.of(unit));
        when(crossMatchRepository.findTopByHospitalIdAndBloodUnitIdAndPatientIdOrderByVerifiedAtDesc(hospitalId, 1L, 55L))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.issueUnit(1L, 55L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("cross-match");
        verify(unitRepository, never()).save(argThat(u -> "ISSUED".equals(u.getStatus())));
    }

    @Test
    void issueUnit_rejectedWhenCrossMatchIncompatible() {
        Long hospitalId = 1L;
        when(securityHelper.getCurrentHospitalId()).thenReturn(hospitalId);
        BloodUnit unit = availableUnit(hospitalId);
        when(unitRepository.findByIdAndHospitalId(1L, hospitalId)).thenReturn(Optional.of(unit));
        CrossMatch xm = new CrossMatch();
        xm.setResult("INCOMPATIBLE");
        when(crossMatchRepository.findTopByHospitalIdAndBloodUnitIdAndPatientIdOrderByVerifiedAtDesc(hospitalId, 1L, 55L))
                .thenReturn(Optional.of(xm));

        assertThatThrownBy(() -> service.issueUnit(1L, 55L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("INCOMPATIBLE");
    }

    @Test
    void issueUnit_succeedsWithCompatibleCrossMatch() {
        Long hospitalId = 1L;
        stubActor();
        when(securityHelper.getCurrentHospitalId()).thenReturn(hospitalId);
        BloodUnit unit = availableUnit(hospitalId);
        unit.setStatus("RESERVED");
        when(unitRepository.findByIdAndHospitalId(1L, hospitalId)).thenReturn(Optional.of(unit));
        CrossMatch xm = new CrossMatch();
        xm.setResult("COMPATIBLE");
        when(crossMatchRepository.findTopByHospitalIdAndBloodUnitIdAndPatientIdOrderByVerifiedAtDesc(hospitalId, 1L, 55L))
                .thenReturn(Optional.of(xm));
        when(unitRepository.save(any(BloodUnit.class))).thenAnswer(i -> i.getArgument(0));

        BloodUnit issued = service.issueUnit(1L, 55L);

        assertThat(issued.getStatus()).isEqualTo("ISSUED");
    }

    @Test
    void issueUnit_rejectedWhenExpired() {
        Long hospitalId = 1L;
        when(securityHelper.getCurrentHospitalId()).thenReturn(hospitalId);
        BloodUnit unit = availableUnit(hospitalId);
        unit.setExpiryDate(LocalDate.now().minusDays(1));
        when(unitRepository.findByIdAndHospitalId(1L, hospitalId)).thenReturn(Optional.of(unit));
        when(unitRepository.save(any(BloodUnit.class))).thenAnswer(i -> i.getArgument(0));

        assertThatThrownBy(() -> service.issueUnit(1L, 55L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("expired");
        assertThat(unit.getStatus()).isEqualTo("EXPIRED");
        verifyNoInteractions(crossMatchRepository);
    }

    // ===== BR-5/BR-6 transfusion + reaction alarm =====

    @Test
    void startTransfusion_requiresIssuedUnit() {
        Long hospitalId = 1L;
        when(securityHelper.getCurrentHospitalId()).thenReturn(hospitalId);
        BloodUnit unit = availableUnit(hospitalId); // AVAILABLE, not ISSUED
        when(unitRepository.findByIdAndHospitalId(1L, hospitalId)).thenReturn(Optional.of(unit));

        TransfusionRequest req = new TransfusionRequest();
        req.setBloodUnitId(1L);
        req.setPatientId(55L);

        assertThatThrownBy(() -> service.startTransfusion(req))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ISSUED");
    }

    @Test
    void completeTransfusion_reactionFreezesActiveAndQuarantinesSiblingUnits() {
        Long hospitalId = 1L;
        stubActor();
        when(securityHelper.getCurrentHospitalId()).thenReturn(hospitalId);

        TransfusionRecord record = new TransfusionRecord();
        record.setId(9L);
        record.setHospitalId(hospitalId);
        record.setPatientId(55L);
        record.setBloodUnitId(1L);
        when(transfusionRepository.findById(9L)).thenReturn(Optional.of(record));
        when(transfusionRepository.save(any(TransfusionRecord.class))).thenAnswer(i -> i.getArgument(0));

        TransfusionRecord otherActive = new TransfusionRecord();
        otherActive.setId(10L);
        otherActive.setPatientId(55L);
        when(transfusionRepository.findByHospitalIdAndPatientIdAndCompletedAtIsNull(hospitalId, 55L))
                .thenReturn(new ArrayList<>(List.of(otherActive)));

        BloodUnit reactedUnit = availableUnit(hospitalId);
        reactedUnit.setDonorId(10L);
        when(unitRepository.findById(1L)).thenReturn(Optional.of(reactedUnit));
        BloodUnit sibling = new BloodUnit();
        sibling.setId(2L);
        sibling.setDonorId(10L);
        sibling.setStatus("AVAILABLE");
        when(unitRepository.findByHospitalIdAndDonorId(hospitalId, 10L)).thenReturn(List.of(sibling));
        when(unitRepository.save(any(BloodUnit.class))).thenAnswer(i -> i.getArgument(0));

        TransfusionCompletionRequest req = new TransfusionCompletionRequest();
        req.setReaction("HEMOLYTIC");
        req.setReactionNotes("Fever + back pain 5 min into transfusion");

        TransfusionRecord completed = service.completeTransfusion(9L, req);

        assertThat(completed.getReaction()).isEqualTo("HEMOLYTIC");
        assertThat(completed.getCompletedAt()).isNotNull();
        assertThat(otherActive.getReaction()).isEqualTo("FROZEN_PENDING_REVIEW");
        assertThat(sibling.getStatus()).isEqualTo("QUARANTINED");
        verify(webSocketHandler, atLeastOnce()).broadcast(eq(hospitalId), contains("TRANSFUSION_REACTION_ALERT"));
    }

    @Test
    void completeTransfusion_noReaction_doesNotFreezeOrQuarantine() {
        Long hospitalId = 1L;
        stubActor();
        when(securityHelper.getCurrentHospitalId()).thenReturn(hospitalId);
        TransfusionRecord record = new TransfusionRecord();
        record.setId(9L);
        record.setHospitalId(hospitalId);
        record.setPatientId(55L);
        record.setBloodUnitId(1L);
        when(transfusionRepository.findById(9L)).thenReturn(Optional.of(record));
        when(transfusionRepository.save(any(TransfusionRecord.class))).thenAnswer(i -> i.getArgument(0));

        TransfusionCompletionRequest req = new TransfusionCompletionRequest();
        req.setReaction("NONE");

        TransfusionRecord completed = service.completeTransfusion(9L, req);

        assertThat(completed.getReaction()).isEqualTo("NONE");
        verifyNoInteractions(unitRepository);
    }
}
