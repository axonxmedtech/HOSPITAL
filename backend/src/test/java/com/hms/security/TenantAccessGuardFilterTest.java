package com.hms.security;

import com.hms.service.subscription.TenantAccessService.State;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** What an ended demo and an expired plan may still do. */
class TenantAccessGuardFilterTest {

    @Test
    void anEndedDemoMayReadButChangesNothing() {
        assertThat(TenantAccessGuardFilter.refuses(State.DEMO_ENDED, "GET", "/patients")).isFalse();
        assertThat(TenantAccessGuardFilter.refuses(State.DEMO_ENDED, "POST", "/patients")).isTrue();
        assertThat(TenantAccessGuardFilter.refuses(State.DEMO_ENDED, "PUT", "/settings/fees")).isTrue();
        assertThat(TenantAccessGuardFilter.refuses(State.DEMO_ENDED, "DELETE", "/doctors/abc")).isTrue();
    }

    @Test
    void reachingSupportAlwaysWorks() {
        assertThat(TenantAccessGuardFilter.refuses(State.DEMO_ENDED, "POST", "/tickets")).isFalse();
        assertThat(TenantAccessGuardFilter.refuses(State.DEMO_ENDED, "PUT", "/notifications/5/read")).isFalse();
        assertThat(TenantAccessGuardFilter.refuses(State.DEMO_ENDED, "PUT", "/auth/profile")).isFalse();
    }

    @Test
    void anExpiredPlanStopsOnlyNewWork() {
        assertThat(TenantAccessGuardFilter.refuses(State.EXPIRED, "POST", "/patients")).isTrue();
        assertThat(TenantAccessGuardFilter.refuses(State.EXPIRED, "POST", "/opd")).isTrue();
        assertThat(TenantAccessGuardFilter.refuses(State.EXPIRED, "POST", "/ipd/admit")).isTrue();
        assertThat(TenantAccessGuardFilter.refuses(State.EXPIRED, "POST", "/doctors")).isTrue();
        // Patients already here are still cared for, discharged and billed.
        assertThat(TenantAccessGuardFilter.refuses(State.EXPIRED, "POST", "/ipd/12/confirm-discharge")).isFalse();
        assertThat(TenantAccessGuardFilter.refuses(State.EXPIRED, "POST", "/billing/9/pay")).isFalse();
        assertThat(TenantAccessGuardFilter.refuses(State.EXPIRED, "POST", "/nurse/vitals")).isFalse();
        assertThat(TenantAccessGuardFilter.refuses(State.EXPIRED, "PUT", "/patients/abc")).isFalse();
    }

    @Test
    void activeGraceAndRunningDemosAreUntouched() {
        for (State s : new State[] {State.ACTIVE, State.EXPIRING, State.GRACE, State.DEMO}) {
            assertThat(TenantAccessGuardFilter.refuses(s, "POST", "/patients")).isFalse();
        }
    }
}
