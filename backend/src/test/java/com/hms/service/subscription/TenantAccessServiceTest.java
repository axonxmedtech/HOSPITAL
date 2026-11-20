package com.hms.service.subscription;

import com.hms.entity.Hospital;
import com.hms.entity.HospitalPlanSubscription;
import com.hms.entity.Plan;
import com.hms.repository.HospitalPlanSubscriptionRepository;
import com.hms.repository.HospitalRepository;
import com.hms.service.subscription.TenantAccessService.State;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/** The demo / expiry rules, without a database. */
class TenantAccessServiceTest {

    private final TenantAccessService svc = new TenantAccessService(
            mock(HospitalRepository.class), mock(HospitalPlanSubscriptionRepository.class), "9022915865", 7, 7);
    private final LocalDateTime now = LocalDateTime.of(2026, 11, 19, 12, 0);

    private static HospitalPlanSubscription sub(LocalDateTime expires) {
        Plan p = new Plan();
        p.setName("Basic");
        HospitalPlanSubscription s = new HospitalPlanSubscription();
        s.setPlan(p);
        s.setExpiresAt(expires);
        return s;
    }

    private static Hospital demo(LocalDateTime ends) {
        Hospital h = new Hospital();
        h.setIsDemo(true);
        h.setDemoEndsAt(ends);
        return h;
    }

    @Test
    void aRunningDemoCountsItsDaysAndNamesTheSupportNumber() {
        var a = svc.compute(demo(now.plusDays(2).plusHours(3)), sub(now.plusDays(30)), now);
        assertThat(a.state()).isEqualTo(State.DEMO);
        assertThat(a.daysLeft()).isEqualTo(3);
        assertThat(a.message()).contains("3 days left").contains("9022915865");
    }

    @Test
    void anEndedDemoIsReadOnlyEvenWithAPaidPlanBehindIt() {
        var a = svc.compute(demo(now.minusMinutes(1)), sub(now.plusDays(30)), now);
        assertThat(a.state()).isEqualTo(State.DEMO_ENDED);
        assertThat(svc.describe(a)).containsEntry("readOnly", true);
        assertThat(a.message()).contains("demo has ended").contains("9022915865");
    }

    @Test
    void aPlanWarnsInItsLastWeekThenHasAGracePeriodThenPausesNewWork() {
        Hospital paid = new Hospital();
        assertThat(svc.compute(paid, sub(now.plusDays(30)), now).state()).isEqualTo(State.ACTIVE);
        assertThat(svc.compute(paid, sub(now.plusDays(5)), now).state()).isEqualTo(State.EXPIRING);
        var grace = svc.compute(paid, sub(now.minusDays(3)), now);
        assertThat(grace.state()).isEqualTo(State.GRACE);
        assertThat(grace.daysLeft()).isEqualTo(4);
        var expired = svc.compute(paid, sub(now.minusDays(8)), now);
        assertThat(expired.state()).isEqualTo(State.EXPIRED);
        assertThat(svc.describe(expired)).containsEntry("newRegistrationsPaused", true).containsEntry("readOnly", false);
    }

    @Test
    void noSubscriptionIsTreatedAsActive() {
        assertThat(svc.compute(new Hospital(), null, now).state()).isEqualTo(State.ACTIVE);
    }
}
