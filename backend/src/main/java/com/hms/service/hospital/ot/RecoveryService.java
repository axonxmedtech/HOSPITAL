package com.hms.service.hospital.ot;

import com.hms.entity.RecoveryEpisode;
import com.hms.entity.RecoveryObservation;
import com.hms.entity.Surgery;
import com.hms.exception.UnauthorizedException;
import com.hms.repository.RecoveryEpisodeRepository;
import com.hms.repository.RecoveryObservationRepository;
import com.hms.repository.SurgeryRepository;
import com.hms.security.PerformingNurseResolver;
import com.hms.security.SecurityContextHelper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

/**
 * RecoveryService - the PACU episode after a procedure, gated by the hospital's
 * RECOVERY_TRACKING policy.
 *
 * NONE: the hospital records nothing (a 10-bed nursing home). MILESTONE: arrival and
 * departure are milestones only. PACU_EPISODE: a full episode with an Aldrete series.
 * The theatre is never held for any of this -- recovery is a record, not a case state.
 */
@Service
public class RecoveryService {

    private static final Set<String> DESTINATIONS = Set.of("WARD", "ICU", "HDU", "HOME", "MORTUARY");

    @Autowired private RecoveryEpisodeRepository episodeRepository;
    @Autowired private RecoveryObservationRepository observationRepository;
    @Autowired private SurgeryRepository surgeryRepository;
    @Autowired private OtPolicyService otPolicyService;
    @Autowired private SecurityContextHelper securityHelper;
    @Autowired private PerformingNurseResolver performingNurseResolver;

    public RecoveryEpisode episode(Long surgeryId) {
        Long hospitalId = requireHospitalId();
        requireSurgery(surgeryId, hospitalId);
        return episodeRepository.findBySurgeryId(surgeryId).orElse(null);
    }

    public List<RecoveryObservation> observations(Long episodeId) {
        return observationRepository.findByEpisodeIdOrderByObservedAtAsc(episodeId);
    }

    /** Admit a patient to recovery. Rejected when the hospital tracks no recovery. */
    @Transactional
    public RecoveryEpisode admit(Long surgeryId) {
        Long hospitalId = requireHospitalId();
        Surgery surgery = requireSurgery(surgeryId, hospitalId);
        assertTracked(hospitalId, surgery);
        if (episodeRepository.findBySurgeryId(surgeryId).isPresent()) {
            throw new IllegalArgumentException("This patient is already in recovery");
        }
        RecoveryEpisode e = new RecoveryEpisode();
        e.setHospitalId(hospitalId);
        e.setSurgeryId(surgery.getId());
        e.setPatientId(surgery.getPatientId());
        e.setArrivedAt(LocalDateTime.now());
        e.setArrivedByUserId(securityHelper.getCurrentUserId());
        return episodeRepository.save(e);
    }

    /** Record an Aldrete observation. Only for PACU_EPISODE tracking. */
    @Transactional
    public RecoveryObservation observe(Long surgeryId, Integer aldreteScore, Long performedByNurseId, String note) {
        Long hospitalId = requireHospitalId();
        requireSurgery(surgeryId, hospitalId);
        RecoveryEpisode e = episodeRepository.findBySurgeryId(surgeryId)
                .orElseThrow(() -> new IllegalArgumentException("Admit the patient to recovery first"));
        if (aldreteScore != null && (aldreteScore < 0 || aldreteScore > 10)) {
            throw new IllegalArgumentException("Aldrete score must be between 0 and 10");
        }
        RecoveryObservation o = new RecoveryObservation();
        o.setHospitalId(hospitalId);
        o.setEpisodeId(e.getId());
        o.setObservedAt(LocalDateTime.now());
        o.setAldreteScore(aldreteScore);
        o.setRecordedByUserId(securityHelper.getCurrentUserId());
        o.setPerformedByNurseId(performingNurseResolver.resolve(performedByNurseId));
        o.setNote(note);
        return observationRepository.save(o);
    }

    /** Discharge from PACU to a destination. */
    @Transactional
    public RecoveryEpisode discharge(Long surgeryId, String destination) {
        Long hospitalId = requireHospitalId();
        requireSurgery(surgeryId, hospitalId);
        RecoveryEpisode e = episodeRepository.findBySurgeryId(surgeryId)
                .orElseThrow(() -> new IllegalArgumentException("This patient is not in recovery"));
        if (destination == null || !DESTINATIONS.contains(destination)) {
            throw new IllegalArgumentException("A valid destination is required (WARD, ICU, HDU, HOME, MORTUARY)");
        }
        e.setDischargedAt(LocalDateTime.now());
        e.setDischargedByUserId(securityHelper.getCurrentUserId());
        e.setTransferDestination(destination);
        return episodeRepository.save(e);
    }

    private void assertTracked(Long hospitalId, Surgery surgery) {
        String mode = otPolicyService.resolve(hospitalId, OtPolicies.RECOVERY_TRACKING, surgery.getPriority());
        if ("NONE".equals(mode)) {
            throw new IllegalArgumentException("This hospital does not track recovery. Enable it under OT Policies.");
        }
    }

    private Surgery requireSurgery(Long surgeryId, Long hospitalId) {
        Surgery s = surgeryRepository.findById(surgeryId)
                .orElseThrow(() -> new IllegalArgumentException("Surgery not found"));
        if (!hospitalId.equals(s.getHospitalId())) {
            throw new UnauthorizedException("Access denied: surgery belongs to another hospital");
        }
        return s;
    }

    private Long requireHospitalId() {
        Long hospitalId = securityHelper.getCurrentHospitalId();
        if (hospitalId == null) throw new UnauthorizedException("Hospital ID not found in context");
        return hospitalId;
    }
}
