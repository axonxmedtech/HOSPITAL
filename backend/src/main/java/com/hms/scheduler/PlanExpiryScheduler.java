package com.hms.scheduler;

import com.hms.entity.Hospital;
import com.hms.repository.HospitalPlanSubscriptionRepository;
import com.hms.repository.HospitalRepository;
import com.hms.service.subscription.TenantAccessService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Map;

/**
 * Keeps each hospital's subscription status label in step with its plan and demo dates, for the
 * Super Admin's lists: ACTIVE, WARNING (expires within a week), GRACE, EXPIRED, DEMO, DEMO_ENDED.
 *
 * <p>It no longer switches a hospital off when its plan lapses. What an expired or ended tenant
 * may still do is decided per request by TenantAccessService; only the Super Admin blocks a
 * tenant outright.
 */
@Component
public class PlanExpiryScheduler {

    private static final Map<TenantAccessService.State, String> LABEL = Map.of(
            TenantAccessService.State.ACTIVE, "ACTIVE",
            TenantAccessService.State.EXPIRING, "WARNING",
            TenantAccessService.State.GRACE, "GRACE",
            TenantAccessService.State.EXPIRED, "EXPIRED",
            TenantAccessService.State.DEMO, "DEMO",
            TenantAccessService.State.DEMO_ENDED, "DEMO_ENDED");

    private final HospitalRepository hospitalRepository;
    private final HospitalPlanSubscriptionRepository subscriptionRepository;
    private final TenantAccessService accessService;

    public PlanExpiryScheduler(HospitalRepository hospitalRepository,
                               HospitalPlanSubscriptionRepository subscriptionRepository,
                               TenantAccessService accessService) {
        this.hospitalRepository = hospitalRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.accessService = accessService;
    }

    /** Hourly, so a demo that ends at 3 pm reads as ended in the lists the same afternoon. */
    @Scheduled(cron = "0 5 * * * *")
    @Transactional
    public void checkPlanExpiry() {
        LocalDateTime now = LocalDateTime.now(ZoneId.systemDefault());
        for (Hospital hospital : hospitalRepository.findAll()) {
            refreshStatus(hospital, now);
        }
    }

    /** Recomputes one hospital's label; saves only when it changed. */
    public void refreshStatus(Hospital hospital, LocalDateTime now) {
        var sub = subscriptionRepository.findByHospitalIdAndIsCurrentTrue(hospital.getId()).orElse(null);
        String label = LABEL.get(accessService.compute(hospital, sub, now).state());
        if (label != null && !label.equals(hospital.getSubscriptionStatus())) {
            hospital.setSubscriptionStatus(label);
            hospitalRepository.save(hospital);
            accessService.evict(hospital.getId());
        }
    }
}
