package com.hms.service.platform;

import com.hms.entitlement.PlanLimit;
import com.hms.entity.AuditLog;
import com.hms.entity.Hospital;
import com.hms.entity.HospitalPlanSubscription;
import com.hms.exception.ResourceNotFoundException;
import com.hms.repository.AuditLogRepository;
import com.hms.repository.BedRepository;
import com.hms.repository.DoctorRepository;
import com.hms.repository.HospitalPlanSubscriptionRepository;
import com.hms.repository.HospitalRepository;
import com.hms.repository.OtRoomRepository;
import com.hms.repository.UserRepository;
import com.hms.scheduler.PlanExpiryScheduler;
import com.hms.service.subscription.PlanLimitService;
import com.hms.service.subscription.TenantAccessService;
import com.hms.service.subscription.TenantAccessService.Access;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The Super Admin's controls over a tenant's commercial state: demo start / extend / end /
 * convert, per-hospital limit overrides, subscription extension, and the overview of who is
 * on a demo, who must renew and who is close to a limit.
 */
@Service
public class PlatformSubscriptionService {

    private static final Logger logger = LoggerFactory.getLogger(PlatformSubscriptionService.class);
    private static final int MAX_DAYS = 365;
    /** A demo ending within this many days is listed as "ending soon". */
    private static final int ENDING_SOON_DAYS = 3;
    /** A limit at or above this share of its cap is "near the limit". */
    private static final double NEAR_LIMIT = 0.8;

    private final HospitalRepository hospitalRepository;
    private final HospitalPlanSubscriptionRepository subscriptionRepository;
    private final TenantAccessService accessService;
    private final PlanLimitService planLimits;
    private final PlanExpiryScheduler statusRefresher;
    private final AuditLogRepository auditLogRepository;
    private final BedRepository bedRepository;
    private final DoctorRepository doctorRepository;
    private final UserRepository userRepository;
    private final OtRoomRepository otRoomRepository;

    public PlatformSubscriptionService(HospitalRepository hospitalRepository,
                                       HospitalPlanSubscriptionRepository subscriptionRepository,
                                       TenantAccessService accessService, PlanLimitService planLimits,
                                       PlanExpiryScheduler statusRefresher, AuditLogRepository auditLogRepository,
                                       BedRepository bedRepository, DoctorRepository doctorRepository,
                                       UserRepository userRepository, OtRoomRepository otRoomRepository) {
        this.hospitalRepository = hospitalRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.accessService = accessService;
        this.planLimits = planLimits;
        this.statusRefresher = statusRefresher;
        this.auditLogRepository = auditLogRepository;
        this.bedRepository = bedRepository;
        this.doctorRepository = doctorRepository;
        this.userRepository = userRepository;
        this.otRoomRepository = otRoomRepository;
    }

    // ─── demo ──────────────────────────────────────────────────────────────

    /** Makes the hospital a demo that ends {@code days} days from now. */
    @Transactional
    public Hospital startDemo(Hospital hospital, Integer days) {
        int d = requireDays(days, "Demo length");
        LocalDateTime now = now();
        hospital.setIsDemo(true);
        hospital.setDemoStartedAt(now);
        hospital.setDemoEndsAt(now.plusDays(d));
        return saveAndAudit(hospital, "DEMO_STARTED", "Demo started for " + d + " days");
    }

    /**
     * Applies a demo action from the Super Admin: START (days), EXTEND (days, from the current end,
     * or from now if it has already ended), END (now), CONVERT (no longer a demo).
     */
    @Transactional
    public Map<String, Object> demoAction(String hospitalPublicId, String action, Integer days) {
        Hospital h = requireHospital(hospitalPublicId);
        String a = action == null ? "" : action.trim().toUpperCase(java.util.Locale.ROOT);
        switch (a) {
            case "START" -> startDemo(h, days);
            case "EXTEND" -> {
                if (!Boolean.TRUE.equals(h.getIsDemo())) {
                    throw new IllegalArgumentException("This account is not a demo. Use Start demo instead.");
                }
                int d = requireDays(days, "Extension");
                LocalDateTime from = h.getDemoEndsAt() != null && h.getDemoEndsAt().isAfter(now()) ? h.getDemoEndsAt() : now();
                h.setDemoEndsAt(from.plusDays(d));
                saveAndAudit(h, "DEMO_EXTENDED", "Demo extended by " + d + " days");
            }
            case "END" -> {
                if (!Boolean.TRUE.equals(h.getIsDemo())) throw new IllegalArgumentException("This account is not a demo.");
                h.setDemoEndsAt(now());
                saveAndAudit(h, "DEMO_ENDED", "Demo ended by the Super Admin");
            }
            case "CONVERT" -> {
                h.setIsDemo(false);
                h.setDemoEndsAt(null);
                saveAndAudit(h, "DEMO_CONVERTED", "Converted from demo to a paid account");
            }
            default -> throw new IllegalArgumentException("Unknown demo action. Use START, EXTEND, END or CONVERT.");
        }
        return accessOf(h);
    }

    // ─── subscription ──────────────────────────────────────────────────────

    /** Pushes the current subscription's expiry out by {@code days} (from now if it already lapsed). */
    @Transactional
    public Map<String, Object> extendSubscription(String hospitalPublicId, Integer days) {
        Hospital h = requireHospital(hospitalPublicId);
        int d = requireDays(days, "Extension");
        HospitalPlanSubscription sub = subscriptionRepository.findByHospitalIdAndIsCurrentTrue(h.getId())
                .orElseThrow(() -> new IllegalArgumentException("This account has no plan yet. Assign a plan first."));
        LocalDateTime from = sub.getExpiresAt() != null && sub.getExpiresAt().isAfter(now()) ? sub.getExpiresAt() : now();
        sub.setExpiresAt(from.plusDays(d));
        subscriptionRepository.save(sub);
        saveAndAudit(h, "SUBSCRIPTION_EXTENDED", "Subscription extended by " + d + " days");
        Map<String, Object> out = accessOf(h);
        out.put("expiresAt", sub.getExpiresAt());
        return out;
    }

    // ─── limits ────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Map<String, Object> limits(String hospitalPublicId) {
        Hospital h = requireHospital(hospitalPublicId);
        Map<String, Object> out = accessOf(h);
        out.put("limits", planLimits.usage(h.getId()));
        return out;
    }

    @Transactional
    public Map<String, Object> setLimitOverrides(String hospitalPublicId, Map<String, Integer> overrides) {
        Hospital h = requireHospital(hospitalPublicId);
        planLimits.setOverrides(h.getId(), overrides);
        audit("LIMITS_OVERRIDDEN", h.getName() + ": " + (overrides == null ? "{}" : overrides));
        return limits(hospitalPublicId);
    }

    // ─── overview ──────────────────────────────────────────────────────────

    /**
     * Every tenant's commercial state in one read: counts for the cards, and the lists the Super
     * Admin acts on — demos (ending soonest first), renewals, and hospitals close to a limit.
     */
    @Transactional(readOnly = true)
    public Map<String, Object> overview() {
        LocalDateTime now = now();
        Map<PlanLimit, Map<Long, Long>> usage = usageByHospital();
        Map<String, Long> counts = new LinkedHashMap<>();
        for (String k : List.of("total", "active", "blocked", "demo", "demoEndingSoon", "demoEnded",
                "expiringSoon", "grace", "expired")) {
            counts.put(k, 0L);
        }
        List<Map<String, Object>> demos = new ArrayList<>();
        List<Map<String, Object>> renewals = new ArrayList<>();
        List<Map<String, Object>> nearLimit = new ArrayList<>();

        for (Hospital h : hospitalRepository.findAll()) {
            HospitalPlanSubscription sub = subscriptionRepository.findByHospitalIdAndIsCurrentTrue(h.getId()).orElse(null);
            Access a = accessService.compute(h, sub, now);
            counts.merge("total", 1L, Long::sum);
            if (!Boolean.TRUE.equals(h.getIsActive())) counts.merge("blocked", 1L, Long::sum);
            Map<String, Object> row = row(h, a);
            switch (a.state()) {
                case DEMO -> {
                    counts.merge("demo", 1L, Long::sum);
                    if (a.daysLeft() >= 0 && a.daysLeft() <= ENDING_SOON_DAYS) counts.merge("demoEndingSoon", 1L, Long::sum);
                    demos.add(row);
                }
                case DEMO_ENDED -> {
                    counts.merge("demoEnded", 1L, Long::sum);
                    demos.add(row);
                }
                case EXPIRING -> {
                    counts.merge("expiringSoon", 1L, Long::sum);
                    renewals.add(row);
                }
                case GRACE -> {
                    counts.merge("grace", 1L, Long::sum);
                    renewals.add(row);
                }
                case EXPIRED -> {
                    counts.merge("expired", 1L, Long::sum);
                    renewals.add(row);
                }
                default -> counts.merge("active", 1L, Long::sum);
            }
            List<Map<String, Object>> near = nearLimits(h, sub, usage);
            if (!near.isEmpty()) {
                Map<String, Object> n = row(h, a);
                n.put("limits", near);
                nearLimit.add(n);
            }
        }
        Comparator<Map<String, Object>> byEnd = Comparator.comparing(
                m -> (LocalDateTime) m.get("endsAt"), Comparator.nullsLast(Comparator.naturalOrder()));
        demos.sort(byEnd);
        renewals.sort(byEnd);

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("counts", counts);
        out.put("demos", demos);
        out.put("renewals", renewals);
        out.put("nearLimit", nearLimit);
        out.put("supportPhone", accessService.supportPhone());
        return out;
    }

    /** Usage of every limit for every hospital, one grouped query per limit. */
    private Map<PlanLimit, Map<Long, Long>> usageByHospital() {
        Map<PlanLimit, Map<Long, Long>> out = new EnumMap<>(PlanLimit.class);
        out.put(PlanLimit.BEDS, toMap(bedRepository.countPerHospital()));
        out.put(PlanLimit.ICU_BEDS, toMap(bedRepository.countIcuPerHospital()));
        out.put(PlanLimit.DOCTORS, toMap(doctorRepository.countActivePerHospital()));
        out.put(PlanLimit.STAFF_LOGINS, toMap(userRepository.countActivePerHospital()));
        out.put(PlanLimit.OT_THEATRES, toMap(otRoomRepository.countActivePerHospital()));
        return out;
    }

    private static Map<Long, Long> toMap(List<Object[]> rows) {
        Map<Long, Long> m = new HashMap<>();
        for (Object[] r : rows) {
            if (r[0] != null) m.put(((Number) r[0]).longValue(), ((Number) r[1]).longValue());
        }
        return m;
    }

    private static List<Map<String, Object>> nearLimits(Hospital h, HospitalPlanSubscription sub,
                                                        Map<PlanLimit, Map<Long, Long>> usage) {
        Map<String, Integer> planCaps = sub != null && sub.getPlan() != null && sub.getPlan().getLimits() != null
                ? sub.getPlan().getLimits() : Map.of();
        List<Map<String, Object>> out = new ArrayList<>();
        for (PlanLimit key : PlanLimit.values()) {
            Integer override = h.getLimitOverrides().get(key.name());
            Integer cap = override != null ? override : planCaps.get(key.name());
            if (cap == null || cap <= 0) continue;
            long used = usage.get(key).getOrDefault(h.getId(), 0L);
            if ((double) used / cap >= NEAR_LIMIT) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("key", key.name());
                m.put("label", key.label());
                m.put("used", used);
                m.put("limit", cap);
                out.add(m);
            }
        }
        return out;
    }

    private static Map<String, Object> row(Hospital h, Access a) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("publicId", h.getPublicId());
        m.put("name", h.getName());
        m.put("type", h.getType() == null ? null : h.getType().name());
        m.put("isActive", h.getIsActive());
        m.put("state", a.state().name());
        m.put("planName", a.planName());
        m.put("endsAt", a.endsAt());
        m.put("daysLeft", a.daysLeft());
        return m;
    }

    // ─── helpers ───────────────────────────────────────────────────────────

    private Map<String, Object> accessOf(Hospital h) {
        accessService.evict(h.getId());
        Map<String, Object> out = new LinkedHashMap<>(accessService.describe(accessService.accessFor(h.getId())));
        out.put("isDemo", h.getIsDemo());
        out.put("demoStartedAt", h.getDemoStartedAt());
        out.put("demoEndsAt", h.getDemoEndsAt());
        return out;
    }

    private Hospital saveAndAudit(Hospital h, String action, String details) {
        Hospital saved = hospitalRepository.save(h);
        statusRefresher.refreshStatus(saved, now());
        accessService.evict(saved.getId());
        audit(action, saved.getName() + ": " + details);
        return saved;
    }

    private Hospital requireHospital(String publicId) {
        return hospitalRepository.findByPublicId(publicId)
                .orElseThrow(() -> new ResourceNotFoundException("Hospital/Clinic/Pharmacy not found"));
    }

    private static int requireDays(Integer days, String what) {
        if (days == null || days < 1 || days > MAX_DAYS) {
            throw new IllegalArgumentException(what + " must be between 1 and " + MAX_DAYS + " days");
        }
        return days;
    }

    private static LocalDateTime now() {
        return LocalDateTime.now(ZoneId.systemDefault());
    }

    private void audit(String action, String details) {
        try {
            AuditLog log = new AuditLog();
            log.setAction(action);
            log.setDetails(details);
            var auth = SecurityContextHolder.getContext().getAuthentication();
            log.setPerformedBy(auth != null ? auth.getName() : "SYSTEM");
            auditLogRepository.save(log);
        } catch (Exception e) {
            logger.warn("Audit log failed for {}: {}", action, e.getMessage());
        }
    }
}
