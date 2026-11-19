package com.hms.service.subscription;

import com.hms.entity.Hospital;
import com.hms.entity.HospitalPlanSubscription;
import com.hms.repository.HospitalPlanSubscriptionRepository;
import com.hms.repository.HospitalRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * What a hospital may do right now, from its demo dates and its subscription.
 *
 * <ul>
 *   <li><b>Demo running</b> — works fully; the app shows a DEMO label with the days left.</li>
 *   <li><b>Demo ended</b> — staff can sign in and look, but no change is accepted.</li>
 *   <li><b>Plan expiring</b> (within 7 days) / <b>grace</b> (up to 7 days after expiry) — works
 *       fully, with a renewal warning.</li>
 *   <li><b>Plan expired</b> (after the grace days) — patients already in the hospital are still
 *       treated, discharged and billed; new registrations, visits, admissions and staff stop.</li>
 * </ul>
 *
 * <p>An expired plan used to switch the hospital off outright, logging out every nurse with
 * patients still in beds. Blocking a tenant completely is now only ever a Super Admin's decision
 * (the hospital's Active switch).
 */
@Service
public class TenantAccessService {

    public enum State { ACTIVE, EXPIRING, GRACE, EXPIRED, DEMO, DEMO_ENDED }

    private static final ZoneId ZONE = ZoneId.systemDefault();
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH);
    private static final long CACHE_MILLIS = 30_000;

    /** Answer for one hospital at one moment. */
    public record Access(State state, LocalDateTime endsAt, long daysLeft, String planName, String message) {
        public boolean demo() {
            return state == State.DEMO || state == State.DEMO_ENDED;
        }
    }

    private record Cached(Access access, long at) {}

    private final HospitalRepository hospitalRepository;
    private final HospitalPlanSubscriptionRepository subscriptionRepository;
    private final String supportPhone;
    private final int graceDays;
    private final int warningDays;
    private final Map<Long, Cached> cache = new ConcurrentHashMap<>();

    public TenantAccessService(HospitalRepository hospitalRepository,
                               HospitalPlanSubscriptionRepository subscriptionRepository,
                               @Value("${hms.support.phone:9022915865}") String supportPhone,
                               @Value("${hms.subscription.grace-days:7}") int graceDays,
                               @Value("${hms.subscription.warning-days:7}") int warningDays) {
        this.hospitalRepository = hospitalRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.supportPhone = supportPhone;
        this.graceDays = graceDays;
        this.warningDays = warningDays;
    }

    public String supportPhone() {
        return supportPhone;
    }

    public int graceDays() {
        return graceDays;
    }

    /** Cached for 30 seconds; the Super Admin's changes call {@link #evict}. */
    public Access accessFor(Long hospitalId) {
        if (hospitalId == null) return null;
        long now = System.currentTimeMillis();
        Cached c = cache.get(hospitalId);
        if (c != null && now - c.at() < CACHE_MILLIS) return c.access();
        Access fresh = hospitalRepository.findById(hospitalId)
                .map(h -> compute(h, subscriptionRepository.findByHospitalIdAndIsCurrentTrue(hospitalId).orElse(null),
                        LocalDateTime.now(ZONE)))
                .orElse(null);
        if (fresh != null) cache.put(hospitalId, new Cached(fresh, now));
        return fresh;
    }

    public void evict(Long hospitalId) {
        if (hospitalId != null) cache.remove(hospitalId);
    }

    /** The rules, without any lookups: also used by the Super Admin overview for every hospital. */
    public Access compute(Hospital h, HospitalPlanSubscription sub, LocalDateTime now) {
        String planName = sub != null && sub.getPlan() != null ? sub.getPlan().getName() : null;
        if (Boolean.TRUE.equals(h.getIsDemo())) {
            LocalDateTime end = h.getDemoEndsAt();
            if (end != null && !now.isBefore(end)) {
                return new Access(State.DEMO_ENDED, end, 0, planName,
                        "Your demo has ended. You can still look around, but changes are switched off. "
                                + "Contact our support team at " + supportPhone + " to upgrade your plan.");
            }
            long left = end == null ? -1 : daysUntil(now, end);
            return new Access(State.DEMO, end, left, planName,
                    end == null ? "Demo account." : "Demo account: " + daysText(left) + " left. Call " + supportPhone
                            + " to upgrade your plan.");
        }
        if (sub == null || sub.getExpiresAt() == null) {
            return new Access(State.ACTIVE, null, -1, planName, null);
        }
        LocalDateTime expires = sub.getExpiresAt();
        if (now.isBefore(expires)) {
            long left = daysUntil(now, expires);
            if (left <= warningDays) {
                return new Access(State.EXPIRING, expires, left, planName,
                        "Your plan expires on " + DAY.format(expires) + " (" + daysText(left) + " left). Call "
                                + supportPhone + " to renew.");
            }
            return new Access(State.ACTIVE, expires, left, planName, null);
        }
        LocalDateTime graceEnd = expires.plusDays(graceDays);
        if (now.isBefore(graceEnd)) {
            long left = daysUntil(now, graceEnd);
            return new Access(State.GRACE, graceEnd, left, planName,
                    "Your plan expired on " + DAY.format(expires) + ". Everything keeps working for "
                            + daysText(left) + "; after that new registrations stop. Call " + supportPhone + " to renew.");
        }
        return new Access(State.EXPIRED, expires, 0, planName,
                "Your plan expired on " + DAY.format(expires) + ". Patients already here can still be treated, "
                        + "discharged and billed, but new registrations are paused. Call " + supportPhone
                        + " to renew your plan.");
    }

    /** Whole days left, counting a part day as a day ("1 day left" until the very end). */
    private static long daysUntil(LocalDateTime now, LocalDateTime end) {
        long hours = Duration.between(now, end).toHours();
        return Math.max(0, (hours + 23) / 24);
    }

    private static String daysText(long days) {
        return days == 1 ? "1 day" : days + " days";
    }

    /** The access state as the app's banner and the Super Admin screens read it. */
    public Map<String, Object> describe(Access a) {
        Map<String, Object> out = new LinkedHashMap<>();
        if (a == null) return out;
        out.put("state", a.state().name());
        out.put("demo", a.demo());
        out.put("endsAt", a.endsAt());
        out.put("daysLeft", a.daysLeft());
        out.put("planName", a.planName());
        out.put("message", a.message());
        out.put("readOnly", a.state() == State.DEMO_ENDED);
        out.put("newRegistrationsPaused", a.state() == State.EXPIRED);
        out.put("supportPhone", supportPhone);
        return out;
    }
}
