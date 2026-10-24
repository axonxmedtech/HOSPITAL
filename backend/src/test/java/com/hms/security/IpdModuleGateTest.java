package com.hms.security;

import com.hms.controller.hospital.IpdAdmissionController;
import com.hms.entity.HospitalType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

import java.lang.reflect.Method;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * IPD module boundary.
 *
 * <p>IPD is sellable to HOSPITAL only ({@code EntitlementRegistry.SELLABLE}), so entitlement
 * enforcement is asserted for that tenant type. The nine mutations move patients into and out of
 * beds, administer items and write prescriptions — a tenant without the module must not do any of
 * them. The four reads stay open for the same reason as OPD: a discharged admission is a record
 * the hospital must still be able to open.
 *
 * <p>This asserts nothing about whether {@code /clinic/ipd} should exist. That drift is a separate,
 * recorded finding and module gating cannot close it, because the aspect skips CLINIC by design.
 */
class IpdModuleGateTest {

    /** The nine mutations S-SEC-3A identified, named so a rename cannot silently drop one. */
    private static final List<String> MUTATIONS = List.of(
            "admitToIpd", "addFollowup", "planDischarge", "confirmDischarge", "administerItems",
            "administerHospitalItems", "addPrescription", "stopPrescription", "changeBed");

    private static final List<String> WITH_IPD = List.of("OPD", "IPD", "BILLING");
    private static final List<String> WITHOUT_IPD = List.of("OPD", "BILLING");

    private final ModuleGateHarness h = new ModuleGateHarness();

    @AfterEach void clear() { ModuleGateHarness.clear(); }

    private void check(Method m) {
        h.aspect.checkModuleAccess(h.joinPointFor(m, IpdAdmissionController.class));
    }

    @Test
    void allNineMutationsCarryTheIpdGate() {
        for (String name : MUTATIONS) {
            Method m = ModuleGateHarness.handler(IpdAdmissionController.class, name);
            assertThat(m.isAnnotationPresent(RequireModule.class))
                    .as("%s is an IPD mutation and must be gated", name).isTrue();
            assertThat(m.getAnnotation(RequireModule.class).value()).isEqualTo("IPD");
        }
    }

    @Test
    void everyMutationOnTheControllerIsAccountedFor_soANewOneCannotSlipThroughUngated() {
        for (Method m : IpdAdmissionController.class.getDeclaredMethods()) {
            if (!ModuleGateHarness.isMutation(m)) continue;
            assertThat(m.isAnnotationPresent(RequireModule.class))
                    .as("mutation %s must carry @RequireModule(\"IPD\")", m.getName()).isTrue();
        }
    }

    @Test
    void everyReadStaysOpen() {
        h.tenant(HospitalType.HOSPITAL, WITHOUT_IPD);
        for (Method m : IpdAdmissionController.class.getDeclaredMethods()) {
            if (!ModuleGateHarness.isRead(m)) continue;
            assertThat(m.isAnnotationPresent(RequireModule.class))
                    .as("read %s must NOT be module-gated", m.getName()).isFalse();
            assertThatCode(() -> check(m))
                    .as("a discharged admission must stay readable after the module is withdrawn")
                    .doesNotThrowAnyException();
        }
    }

    @Test
    void mutationsAreAllowedWithTheModuleAndDeniedWithoutIt() {
        for (String name : MUTATIONS) {
            Method m = ModuleGateHarness.handler(IpdAdmissionController.class, name);

            h.tenant(HospitalType.HOSPITAL, WITH_IPD);
            assertThatCode(() -> check(m)).as("%s must work when IPD is held", name)
                    .doesNotThrowAnyException();

            h.tenant(HospitalType.HOSPITAL, WITHOUT_IPD);
            assertThatThrownBy(() -> check(m)).as("%s must be denied without IPD", name)
                    .isInstanceOf(AccessDeniedException.class)
                    .hasMessageContaining("IPD");
        }
    }

    @Test
    void admissionIsRevokedMidSession_andNeverReachesTheAdmissionService() {
        Method admit = ModuleGateHarness.handler(IpdAdmissionController.class, "admitToIpd");
        h.tenant(HospitalType.HOSPITAL, WITH_IPD);
        assertThatCode(() -> check(admit)).doesNotThrowAnyException();

        h.liveRowHolds(WITHOUT_IPD); // plan changed; same principal, same stale claim
        assertThatThrownBy(() -> check(admit))
                .as("the next request must be refused without a re-login")
                .isInstanceOf(AccessDeniedException.class);

        // @Before advice: throwing here means IpdAdmissionService was never entered, so no
        // admission row, no bed transition and no prescription can have been written.
        assertThatThrownBy(() -> check(admit)).isInstanceOf(AccessDeniedException.class);
    }
}
