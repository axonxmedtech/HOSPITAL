package com.hms.security;

import com.hms.controller.hospital.OpdController;
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
 * OPD module boundary: gate the write, keep the history readable.
 *
 * <p>A hospital that stops paying for OPD must not be able to open new cases, but the cases it
 * already has are clinical records — the queue, the case-paper PDF and the report are how anyone
 * reads them afterwards. Gating the whole controller would make a withdrawn module erase access to
 * the tenant's own history, which is why only {@code createOpd} carries the annotation.
 */
class OpdModuleGateTest {

    private static final List<String> WITH_OPD = List.of("OPD", "BILLING");
    private static final List<String> WITHOUT_OPD = List.of("BILLING");

    private final ModuleGateHarness h = new ModuleGateHarness();

    @AfterEach void clear() { ModuleGateHarness.clear(); }

    private void check(Method m) { h.aspect.checkModuleAccess(h.joinPointFor(m, OpdController.class)); }

    @Test
    void theOnlyMutationIsGated_andEveryMutationEverAddedMustBe() {
        long mutations = 0;
        for (Method m : OpdController.class.getDeclaredMethods()) {
            if (!ModuleGateHarness.isMutation(m)) continue;
            mutations++;
            assertThat(m.isAnnotationPresent(RequireModule.class))
                    .as("mutation %s must carry @RequireModule(\"OPD\")", m.getName()).isTrue();
            assertThat(m.getAnnotation(RequireModule.class).value()).isEqualTo("OPD");
        }
        assertThat(mutations).as("OpdController should still expose exactly one mutation").isEqualTo(1);
    }

    @Test
    void everyReadStaysOpen_soAWithdrawnModuleDoesNotEraseTheTenantsOwnHistory() {
        h.tenant(HospitalType.HOSPITAL, WITHOUT_OPD);
        for (Method m : OpdController.class.getDeclaredMethods()) {
            if (!ModuleGateHarness.isRead(m)) continue;
            assertThat(m.isAnnotationPresent(RequireModule.class))
                    .as("read %s must NOT be module-gated", m.getName()).isFalse();
            assertThatCode(() -> check(m))
                    .as("read %s must survive the module being withdrawn", m.getName())
                    .doesNotThrowAnyException();
        }
    }

    @Test
    void creatingAnOpdIsAllowedWithTheModuleAndDeniedWithoutIt() {
        Method create = ModuleGateHarness.handler(OpdController.class, "createOpd");

        h.tenant(HospitalType.HOSPITAL, WITH_OPD);
        assertThatCode(() -> check(create)).doesNotThrowAnyException();

        h.tenant(HospitalType.HOSPITAL, WITHOUT_OPD);
        assertThatThrownBy(() -> check(create))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("OPD");
    }

    @Test
    void theModuleIsRevokedOnTheNextRequest_notAtTheNextLogin() {
        Method create = ModuleGateHarness.handler(OpdController.class, "createOpd");
        h.tenant(HospitalType.HOSPITAL, WITH_OPD);
        assertThatCode(() -> check(create)).doesNotThrowAnyException();

        // The plan changes. The principal — and its stale "OPD" claim — is untouched.
        h.liveRowHolds(WITHOUT_OPD);
        assertThatThrownBy(() -> check(create))
                .as("the live hospital row decides, not the token the user is still holding")
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void theDeniedMutationNeverReachesTheServiceThatWouldHaveWrittenTheCase() {
        // The aspect is @Before: it throws ahead of the handler, so no OpdService call, no OPD row
        // and no side effect can occur. Proven structurally — the join point is never proceeded.
        Method create = ModuleGateHarness.handler(OpdController.class, "createOpd");
        h.tenant(HospitalType.HOSPITAL, WITHOUT_OPD);
        assertThatThrownBy(() -> check(create)).isInstanceOf(AccessDeniedException.class);
        // A @Before advice that throws cannot proceed to the target, so OpdService is never
        // entered: no OPD row, no bill, no queue entry. The denial is inert by construction.
    }

    @Test
    void clinicAndPharmacySessionsAreUnaffected_becauseTheAspectSkipsThoseTenantTypes() {
        Method create = ModuleGateHarness.handler(OpdController.class, "createOpd");
        // A clinic legitimately runs OPD through the /clinic/opd alias; it is never module-gated.
        h.tenant(HospitalType.CLINIC, WITHOUT_OPD);
        assertThatCode(() -> check(create))
                .as("adding the annotation must not 403 a clinic tenant").doesNotThrowAnyException();
        // The /pharmacy/opd alias exists only because of the frontend namespace rewrite. A pharmacy
        // tenant can never hold OPD, and the skip is what keeps the annotation harmless for it.
        h.tenant(HospitalType.PHARMACY, WITHOUT_OPD);
        assertThatCode(() -> check(create)).doesNotThrowAnyException();
    }
}
