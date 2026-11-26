package com.hms.security;

import com.hms.controller.hospital.BillingController;
import com.hms.controller.hospital.HospitalServiceController;
import com.hms.entitlement.EntitlementRegistry;
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
 * Billing changes need BILLING on the plan; reads stay open, so old bills can always be opened
 * and printed. Safe because IPD includes BILLING ({@code EntitlementRegistry}): a hospital that
 * admits patients can always settle the bill at discharge.
 *
 * <p>The hospital services catalog is managed from the Hospital Inventory screen, so its changes
 * need HOSPITAL_INVENTORY. Its reads stay open: bills and OPD screens read the list.
 */
class BillingModuleGateTest {

    private final ModuleGateHarness h = new ModuleGateHarness();

    @AfterEach void clear() { ModuleGateHarness.clear(); }

    private void assertMutationsGated(Class<?> controller, String module, List<String> with, List<String> without) {
        int mutations = 0;
        for (Method m : controller.getDeclaredMethods()) {
            if (!ModuleGateHarness.isMutation(m)) continue;
            mutations++;
            assertThat(m.isAnnotationPresent(RequireModule.class))
                    .as("mutation %s.%s must carry @RequireModule(\"%s\")", controller.getSimpleName(), m.getName(), module)
                    .isTrue();
            assertThat(m.getAnnotation(RequireModule.class).value()).isEqualTo(module);

            h.tenant(HospitalType.HOSPITAL, with);
            assertThatCode(() -> h.aspect.checkModuleAccess(h.joinPointFor(m, controller)))
                    .as("%s must work with %s", m.getName(), module).doesNotThrowAnyException();

            h.tenant(HospitalType.HOSPITAL, without);
            assertThatThrownBy(() -> h.aspect.checkModuleAccess(h.joinPointFor(m, controller)))
                    .as("%s must be denied without %s", m.getName(), module)
                    .isInstanceOf(AccessDeniedException.class)
                    .hasMessageContaining(module);
        }
        assertThat(mutations).isPositive();
    }

    private void assertReadsOpen(Class<?> controller, List<String> modules) {
        h.tenant(HospitalType.HOSPITAL, modules);
        for (Method m : controller.getDeclaredMethods()) {
            if (!ModuleGateHarness.isRead(m)) continue;
            assertThat(m.isAnnotationPresent(RequireModule.class)).as("read %s", m.getName()).isFalse();
            assertThatCode(() -> h.aspect.checkModuleAccess(h.joinPointFor(m, controller)))
                    .doesNotThrowAnyException();
        }
    }

    @Test
    void billingChangesNeedBilling_readsStayOpen() {
        assertMutationsGated(BillingController.class, "BILLING",
                List.of("OPD", "BILLING"), List.of("OPD"));
        assertReadsOpen(BillingController.class, List.of("OPD"));
    }

    @Test
    void servicesCatalogChangesNeedHospitalInventory_readsStayOpen() {
        assertMutationsGated(HospitalServiceController.class, "HOSPITAL_INVENTORY",
                List.of("OPD", "BILLING", "HOSPITAL_INVENTORY"), List.of("OPD", "BILLING"));
        assertReadsOpen(HospitalServiceController.class, List.of("OPD"));
    }

    @Test
    void aHospitalSoldIpdAlwaysHoldsBilling_soDischargeCanSettleTheBill() {
        List<String> applied = EntitlementRegistry.normalizeAppliedPlanModules(HospitalType.HOSPITAL, List.of("OPD", "IPD"));
        Method pay = ModuleGateHarness.handler(BillingController.class, "payBilling");

        h.tenant(HospitalType.HOSPITAL, applied);
        assertThatCode(() -> h.aspect.checkModuleAccess(h.joinPointFor(pay, BillingController.class)))
                .doesNotThrowAnyException();
    }
}
