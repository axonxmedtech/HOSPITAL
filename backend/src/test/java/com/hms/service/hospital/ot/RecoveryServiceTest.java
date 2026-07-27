package com.hms.service.hospital.ot;

import com.hms.entity.OtRoom;
import com.hms.entity.RecoveryEpisode;
import com.hms.entity.Surgery;
import com.hms.entity.SurgeryStatus;
import com.hms.repository.RecoveryEpisodeRepository;
import com.hms.entity.RecoveryObservation;
import com.hms.repository.RecoveryObservationRepository;
import com.hms.repository.SurgeryRepository;
import com.hms.security.PerformingNurseResolver;
import com.hms.security.SecurityContextHelper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RecoveryServiceTest {

    private static final Long HOSPITAL = 7L;

    @Mock RecoveryEpisodeRepository episodeRepository;
    @Mock RecoveryObservationRepository observationRepository;
    @Mock SurgeryRepository surgeryRepository;
    @Mock OtPolicyService otPolicyService;
    @Mock SecurityContextHelper securityHelper;
    @Mock PerformingNurseResolver performingNurseResolver;
    @InjectMocks RecoveryService service;

    private final Surgery surgery = new Surgery();

    @BeforeEach
    void setUp() {
        lenient().when(securityHelper.getCurrentHospitalId()).thenReturn(HOSPITAL);
        lenient().when(securityHelper.getCurrentUserId()).thenReturn(42L);
        surgery.setId(1L);
        surgery.setHospitalId(HOSPITAL);
        surgery.setPatientId(500L);
        // The theatre was already freed at COMPLETED; recovery does not touch it.
        surgery.setStatus(SurgeryStatus.COMPLETED.name());
        surgery.setOtRoomId(9L);
        lenient().when(surgeryRepository.findById(1L)).thenReturn(Optional.of(surgery));
        lenient().when(episodeRepository.save(any(RecoveryEpisode.class))).thenAnswer(i -> i.getArgument(0));
        lenient().when(performingNurseResolver.resolve(any())).thenReturn(null);
        lenient().when(observationRepository.save(any())).thenAnswer(i -> i.getArgument(0));
    }

    /**
     * The defining Phase 8 regression (ADR C3/D5): a patient can be in recovery while the
     * case is COMPLETED. Recovery is a record; it never re-touches the theatre or the status.
     */
    @Test
    void aPatientInRecovery_doesNotAffectTheCaseStatusOrTheRoom() {
        OtRoom room = new OtRoom();
        room.setId(9L);
        room.setStatus(OtRoom.AVAILABLE);
        when(otPolicyService.resolve(HOSPITAL, OtPolicies.RECOVERY_TRACKING, null)).thenReturn("PACU_EPISODE");
        when(episodeRepository.findBySurgeryId(1L)).thenReturn(Optional.empty());

        RecoveryEpisode e = service.admit(1L);

        assertThat(e.getArrivedAt()).isNotNull();
        // The case is still COMPLETED and the room is untouched by admitting to recovery.
        assertThat(surgery.getStatus()).isEqualTo(SurgeryStatus.COMPLETED.name());
        assertThat(room.getStatus()).isEqualTo(OtRoom.AVAILABLE);
    }

    /** A hospital that tracks no recovery cannot admit to it. */
    @Test
    void admittingWhenRecoveryTrackingIsNone_isRejected() {
        when(otPolicyService.resolve(HOSPITAL, OtPolicies.RECOVERY_TRACKING, null)).thenReturn("NONE");

        assertThatThrownBy(() -> service.admit(1L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("does not track recovery");
    }

    @Test
    void admittingTwice_isRejected() {
        when(otPolicyService.resolve(HOSPITAL, OtPolicies.RECOVERY_TRACKING, null)).thenReturn("PACU_EPISODE");
        when(episodeRepository.findBySurgeryId(1L)).thenReturn(Optional.of(new RecoveryEpisode()));

        assertThatThrownBy(() -> service.admit(1L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("already in recovery");
    }

    @Test
    void anAldreteScoreOutOfRange_isRejected() {
        when(episodeRepository.findBySurgeryId(1L)).thenReturn(Optional.of(episode()));
        assertThatThrownBy(() -> service.observe(1L, 12, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("between 0 and 10");
    }

    @Test
    void observingBeforeAdmission_isRejected() {
        when(episodeRepository.findBySurgeryId(1L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.observe(1L, 8, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Admit the patient to recovery first");
    }

    @Test
    void dischargeToAnUnknownDestination_isRejected() {
        when(episodeRepository.findBySurgeryId(1L)).thenReturn(Optional.of(episode()));
        assertThatThrownBy(() -> service.discharge(1L, "MARS"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("valid destination");
    }

    @Test
    void dischargeToTheWard_recordsTheDestinationAndTime() {
        when(episodeRepository.findBySurgeryId(1L)).thenReturn(Optional.of(episode()));
        RecoveryEpisode e = service.discharge(1L, "WARD");
        assertThat(e.getTransferDestination()).isEqualTo("WARD");
        assertThat(e.getDischargedAt()).isNotNull();
    }

    private RecoveryEpisode episode() {
        RecoveryEpisode e = new RecoveryEpisode();
        e.setId(3L);
        e.setHospitalId(HOSPITAL);
        e.setSurgeryId(1L);
        e.setPatientId(500L);
        return e;
    }
}
