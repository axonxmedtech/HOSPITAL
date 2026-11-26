package com.hms.security;

import com.hms.controller.hospital.IcuCleaningController;
import com.hms.controller.hospital.OtInchargeController;
import com.hms.entity.HospitalType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.security.access.AccessDeniedException;

import java.lang.reflect.Method;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * ICU housekeeping and OT in-charge logins were the only ICU / OT controllers without a plan
 * gate, so a hospital whose plan lacks the module could still use them. Same shape as the IPD
 * boundary: every mutation is gated (found by reflection, so a new one cannot slip through
 * ungated), every read stays open.
 */
class IcuOtModuleGateTest {

    private final ModuleGateHarness h = new ModuleGateHarness();

    @AfterEach void clear() { ModuleGateHarness.clear(); }

    static Stream<Arguments> controllers() {
        return Stream.of(
                Arguments.of(IcuCleaningController.class, "ICU"),
                Arguments.of(OtInchargeController.class, "OT"));
    }

    @ParameterizedTest(name = "{0} mutations need {1}")
    @MethodSource("controllers")
    void everyMutationIsGated_allowedWithTheModule_deniedWithout(Class<?> controller, String module) {
        int mutations = 0;
        for (Method m : controller.getDeclaredMethods()) {
            if (!ModuleGateHarness.isMutation(m)) continue;
            mutations++;
            assertThat(m.isAnnotationPresent(RequireModule.class))
                    .as("mutation %s.%s must carry @RequireModule(\"%s\")", controller.getSimpleName(), m.getName(), module)
                    .isTrue();
            assertThat(m.getAnnotation(RequireModule.class).value()).isEqualTo(module);

            h.tenant(HospitalType.HOSPITAL, List.of("OPD", "IPD", module));
            assertThatCode(() -> h.aspect.checkModuleAccess(h.joinPointFor(m, controller)))
                    .doesNotThrowAnyException();

            h.tenant(HospitalType.HOSPITAL, List.of("OPD", "IPD"));
            assertThatThrownBy(() -> h.aspect.checkModuleAccess(h.joinPointFor(m, controller)))
                    .isInstanceOf(AccessDeniedException.class)
                    .hasMessageContaining(module);
        }
        assertThat(mutations).as("%s has mutations to gate", controller.getSimpleName()).isPositive();
    }

    @ParameterizedTest(name = "{0} reads stay open")
    @MethodSource("controllers")
    void readsStayOpen(Class<?> controller, String module) {
        h.tenant(HospitalType.HOSPITAL, List.of("OPD", "IPD"));
        for (Method m : controller.getDeclaredMethods()) {
            if (!ModuleGateHarness.isRead(m)) continue;
            assertThat(m.isAnnotationPresent(RequireModule.class)).as("read %s", m.getName()).isFalse();
            assertThatCode(() -> h.aspect.checkModuleAccess(h.joinPointFor(m, controller)))
                    .doesNotThrowAnyException();
        }
    }
}
