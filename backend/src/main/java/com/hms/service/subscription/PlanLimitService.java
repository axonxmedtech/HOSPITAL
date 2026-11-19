package com.hms.service.subscription;

import com.hms.entitlement.PlanLimit;
import com.hms.entity.HospitalPlanSubscription;
import com.hms.entity.IcuWard;
import com.hms.exception.PlanLimitReachedException;
import com.hms.repository.BedRepository;
import com.hms.repository.DoctorRepository;
import com.hms.repository.HospitalPlanSubscriptionRepository;
import com.hms.repository.HospitalRepository;
import com.hms.repository.IcuWardRepository;
import com.hms.repository.OtRoomRepository;
import com.hms.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Plan capacity limits: how many beds, ICU beds, doctors, staff logins and operation theatres a
 * hospital may have. The hospital's own override wins over its plan; no number means unlimited.
 *
 * <p>Only ever blocks <em>adding</em>. A hospital that is over its limit — after a downgrade, or
 * a plan whose limit was lowered — keeps everything it has; it just cannot add more until it is
 * back under. Nothing here touches patient care.
 */
@Service
public class PlanLimitService {

    private final HospitalRepository hospitalRepository;
    private final HospitalPlanSubscriptionRepository subscriptionRepository;
    private final BedRepository bedRepository;
    private final IcuWardRepository icuWardRepository;
    private final DoctorRepository doctorRepository;
    private final UserRepository userRepository;
    private final OtRoomRepository otRoomRepository;
    private final String supportPhone;

    public PlanLimitService(HospitalRepository hospitalRepository,
                            HospitalPlanSubscriptionRepository subscriptionRepository,
                            BedRepository bedRepository, IcuWardRepository icuWardRepository,
                            DoctorRepository doctorRepository, UserRepository userRepository,
                            OtRoomRepository otRoomRepository,
                            @Value("${hms.support.phone:9022915865}") String supportPhone) {
        this.hospitalRepository = hospitalRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.bedRepository = bedRepository;
        this.icuWardRepository = icuWardRepository;
        this.doctorRepository = doctorRepository;
        this.userRepository = userRepository;
        this.otRoomRepository = otRoomRepository;
        this.supportPhone = supportPhone;
    }

    public String supportPhone() {
        return supportPhone;
    }

    /** The cap in force for this hospital, or null when unlimited. */
    @Transactional(readOnly = true)
    public Integer limitFor(Long hospitalId, PlanLimit key) {
        Integer override = hospitalRepository.findById(hospitalId)
                .map(h -> h.getLimitOverrides().get(key.name())).orElse(null);
        return override != null ? override : planLimit(hospitalId, key);
    }

    /** The cap the hospital's current plan sets, ignoring any override; null when unlimited. */
    @Transactional(readOnly = true)
    public Integer planLimit(Long hospitalId, PlanLimit key) {
        return subscriptionRepository.findByHospitalIdAndIsCurrentTrue(hospitalId)
                .map(HospitalPlanSubscription::getPlan)
                .map(p -> p.getLimits() == null ? null : p.getLimits().get(key.name()))
                .orElse(null);
    }

    /** How many the hospital has now. */
    public long used(Long hospitalId, PlanLimit key) {
        return switch (key) {
            case BEDS -> bedRepository.countByHospitalId(hospitalId);
            case ICU_BEDS -> {
                List<Long> icuWards = icuWardRepository.findByHospitalId(hospitalId).stream()
                        .map(IcuWard::getWardId).toList();
                yield icuWards.isEmpty() ? 0 : bedRepository.countByHospitalIdAndWardIdIn(hospitalId, icuWards);
            }
            case DOCTORS -> doctorRepository.countByHospitalIdAndIsActiveTrue(hospitalId);
            case STAFF_LOGINS -> userRepository.countActiveByHospitalId(hospitalId);
            case OT_THEATRES -> otRoomRepository.countByHospitalIdAndIsActiveTrue(hospitalId);
        };
    }

    /**
     * Refuses to add {@code adding} more when that would pass the limit. Call it before creating.
     *
     * <p>Inside a transaction the hospital row is locked first, so two people adding the last
     * bed at the same moment cannot both get it.
     */
    @Transactional
    public void requireRoom(Long hospitalId, PlanLimit key, long adding) {
        if (hospitalId == null || adding <= 0) return;
        Integer limit = limitFor(hospitalId, key);
        if (limit == null) return;
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            hospitalRepository.lockById(hospitalId);
        }
        long used = used(hospitalId, key);
        if (used + adding <= limit) return;
        throw new PlanLimitReachedException(refusal(hospitalId, key, limit, used, adding));
    }

    public void requireRoom(Long hospitalId, PlanLimit key) {
        requireRoom(hospitalId, key, 1);
    }

    private String refusal(Long hospitalId, PlanLimit key, int limit, long used, long adding) {
        String plan = subscriptionRepository.findByHospitalIdAndIsCurrentTrue(hospitalId)
                .map(s -> s.getPlan().getName()).map(n -> "your " + n + " plan").orElse("your plan");
        String upgrade = " Call " + supportPhone + " to upgrade your plan.";
        if (limit == 0) {
            return key.pluralTitle() + " are not included in " + plan + "." + upgrade;
        }
        long room = Math.max(0, limit - used);
        String head = key.singularTitle() + " limit reached: " + capitalise(plan) + " allows " + key.count(limit);
        String body = room == 0 || adding == 1
                ? ", and " + used + (used == 1 ? " is" : " are") + " already set up."
                : ". " + used + (used == 1 ? " is" : " are") + " already set up, so only " + room + " more can be added.";
        return head + body + upgrade;
    }

    private static String capitalise(String s) {
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    /** Every limit with its cap and current use, for the usage page and the Super Admin. */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> usage(Long hospitalId) {
        Map<String, Integer> overrides = hospitalRepository.findById(hospitalId)
                .map(h -> Map.copyOf(h.getLimitOverrides().entrySet().stream()
                        .filter(e -> e.getValue() != null)
                        .collect(java.util.stream.Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue))))
                .orElse(Map.of());
        List<Map<String, Object>> out = new ArrayList<>();
        for (PlanLimit key : PlanLimit.values()) {
            Integer plan = planLimit(hospitalId, key);
            Integer override = overrides.get(key.name());
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("key", key.name());
            row.put("label", key.label());
            row.put("used", used(hospitalId, key));
            row.put("planLimit", plan);
            row.put("override", override);
            row.put("limit", override != null ? override : plan);
            out.add(row);
        }
        return out;
    }

    /**
     * Replaces the hospital's overrides. A null or blank value removes that override (the plan's
     * limit applies again); a negative one is refused.
     */
    @Transactional
    public void setOverrides(Long hospitalId, Map<String, Integer> overrides) {
        var hospital = hospitalRepository.findById(hospitalId)
                .orElseThrow(() -> new com.hms.exception.ResourceNotFoundException("Hospital not found"));
        Map<String, Integer> clean = normalisePlanLimits(overrides);
        hospital.getLimitOverrides().clear();
        hospital.getLimitOverrides().putAll(clean);
        hospitalRepository.save(hospital);
    }

    /** Normalises a plan's limits from a request: known keys only, non-negative, null = unlimited. */
    public static Map<String, Integer> normalisePlanLimits(Map<String, Integer> requested) {
        Map<String, Integer> out = new java.util.HashMap<>();
        if (requested == null) return out;
        requested.forEach((k, v) -> {
            PlanLimit key = PlanLimit.fromKey(k);
            if (key == null || v == null) return;
            if (v < 0) throw new IllegalArgumentException(key.label() + " limit cannot be negative");
            out.put(key.name(), v);
        });
        return out;
    }
}
