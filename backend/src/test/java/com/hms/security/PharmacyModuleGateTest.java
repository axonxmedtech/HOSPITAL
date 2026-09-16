package com.hms.security;

import com.hms.controller.hospital.PharmacyController;
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
 * PHARMACY module boundary, which is deliberately asymmetric.
 *
 * <p>For a HOSPITAL tenant, pharmacy is a module it either bought or did not: the embedded
 * mutations and the whole ERP surface are gated. For a standalone PHARMACY tenant the same
 * annotations must be inert, because pharmacy is not a module of its product — it <i>is</i> the
 * product. {@link ModuleAccessAspect} skips non-HOSPITAL tenant types, and the test below pins
 * that behaviour: if someone ever "tidies up" that skip, a standalone pharmacy would 403 itself
 * out of its own ERP, and this test is what catches it.
 */
class PharmacyModuleGateTest {

    /** The ERP controllers that are wholly the pharmacy product. */
    private static final List<Class<?>> ERP = List.of(
            com.hms.controller.pharmacy.InventoryController.class,
            com.hms.controller.pharmacy.ManufacturerController.class,
            com.hms.controller.pharmacy.MedicineCategoryController.class,
            com.hms.controller.pharmacy.MedicineMasterController.class,
            com.hms.controller.pharmacy.PharmacyBranchController.class,
            com.hms.controller.pharmacy.PharmacyReportsController.class,
            com.hms.controller.pharmacy.PharmacySaleController.class,
            com.hms.controller.pharmacy.PurchaseController.class,
            com.hms.controller.pharmacy.SupplierController.class);

    private static final List<String> EMBEDDED_MUTATIONS = List.of("updateStock", "dispenseMedicine");
    private static final List<String> WITH_PHARMACY = List.of("OPD", "PHARMACY");
    private static final List<String> WITHOUT_PHARMACY = List.of("OPD");

    private final ModuleGateHarness h = new ModuleGateHarness();

    @AfterEach void clear() { ModuleGateHarness.clear(); }

    private void check(Method m, Class<?> target) {
        h.aspect.checkModuleAccess(h.joinPointFor(m, target));
    }

    @Test
    void everyErpControllerCarriesTheClassLevelGate() {
        for (Class<?> controller : ERP) {
            RequireModule gate = controller.getAnnotation(RequireModule.class);
            assertThat(gate).as("%s is the pharmacy product and must be gated",
                    controller.getSimpleName()).isNotNull();
            assertThat(gate.value()).isEqualTo("PHARMACY");
        }
        assertThat(ERP).hasSize(9);
    }

    @Test
    void embeddedPharmacyMutationsAreGatedAndItsReadsAreNot() {
        for (String name : EMBEDDED_MUTATIONS) {
            Method m = ModuleGateHarness.handler(PharmacyController.class, name);
            assertThat(m.isAnnotationPresent(RequireModule.class))
                    .as("%s must be gated", name).isTrue();
            assertThat(m.getAnnotation(RequireModule.class).value()).isEqualTo("PHARMACY");
        }
        for (Method m : PharmacyController.class.getDeclaredMethods()) {
            if (ModuleGateHarness.isMutation(m)) {
                assertThat(m.isAnnotationPresent(RequireModule.class))
                        .as("mutation %s must carry the gate", m.getName()).isTrue();
            } else if (ModuleGateHarness.isRead(m)) {
                assertThat(m.isAnnotationPresent(RequireModule.class))
                        .as("read %s must stay open", m.getName()).isFalse();
            }
        }
    }

    @Test
    void aHospitalWithoutThePharmacyModuleCannotDispenseOrRestock() {
        for (String name : EMBEDDED_MUTATIONS) {
            Method m = ModuleGateHarness.handler(PharmacyController.class, name);

            h.tenant(HospitalType.HOSPITAL, WITH_PHARMACY);
            assertThatCode(() -> check(m, PharmacyController.class)).doesNotThrowAnyException();

            h.tenant(HospitalType.HOSPITAL, WITHOUT_PHARMACY);
            assertThatThrownBy(() -> check(m, PharmacyController.class))
                    .as("%s must be denied without the PHARMACY module", name)
                    .isInstanceOf(AccessDeniedException.class)
                    .hasMessageContaining("PHARMACY");
        }
    }

    @Test
    void aHospitalWithoutTheModuleIsAlsoLockedOutOfTheErpSurface() {
        Method sale = ModuleGateHarness.handler(
                com.hms.controller.pharmacy.PharmacySaleController.class, "createSale");
        h.tenant(HospitalType.HOSPITAL, WITHOUT_PHARMACY);
        assertThatThrownBy(() -> check(sale, com.hms.controller.pharmacy.PharmacySaleController.class))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("PHARMACY");
    }

    @Test
    void theModuleIsRevokedOnTheNextRequest_andTheDeniedSaleNeverReachesTheLedger() {
        Method dispense = ModuleGateHarness.handler(PharmacyController.class, "dispenseMedicine");
        h.tenant(HospitalType.HOSPITAL, WITH_PHARMACY);
        assertThatCode(() -> check(dispense, PharmacyController.class)).doesNotThrowAnyException();

        h.liveRowHolds(WITHOUT_PHARMACY);
        assertThatThrownBy(() -> check(dispense, PharmacyController.class))
                .as("a withdrawn plan stops dispensing on the very next request")
                .isInstanceOf(AccessDeniedException.class);
        // @Before advice: the handler is never entered, so no stock decrement and no ledger row.
    }

    @Test
    void aStandalonePharmacyTenantKeepsItsEntireProduct_despiteTheClassLevelGate() {
        // The decisive case. A PHARMACY tenant's plan carries the PHARMACY base module, but the
        // aspect skips the tenant type before it ever reads the annotation. Assert it with the
        // module ABSENT from the row, so the pass cannot come from the module happening to be there.
        h.tenant(HospitalType.PHARMACY, List.of());
        for (Class<?> controller : ERP) {
            for (Method m : controller.getDeclaredMethods()) {
                if (!ModuleGateHarness.isMutation(m) && !ModuleGateHarness.isRead(m)) continue;
                assertThatCode(() -> check(m, controller))
                        .as("standalone pharmacy must keep %s.%s", controller.getSimpleName(), m.getName())
                        .doesNotThrowAnyException();
            }
        }
    }

    @Test
    void aClinicWithTheModuleWithdrawnIsAlsoUnaffected() {
        h.tenant(HospitalType.CLINIC, List.of());
        Method m = ModuleGateHarness.handler(PharmacyController.class, "dispenseMedicine");
        assertThatCode(() -> check(m, PharmacyController.class)).doesNotThrowAnyException();
    }
}
