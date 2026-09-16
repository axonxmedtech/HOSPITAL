package com.hms.security;

import com.hms.entitlement.EntitlementRegistry;
import com.hms.entity.Hospital;
import com.hms.entity.HospitalType;
import com.hms.repository.HospitalRepository;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.bind.annotation.RestController;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * {@code @RequireModule} takes a raw String, so nothing stops someone writing
 * {@code @RequireModule("PHARAMCY")}.
 *
 * <p>What that actually does is the first thing this class establishes, because the answer decides
 * whether a production fix is warranted: an unknown identifier can never appear in a plan's module
 * list, so the gate <b>denies everyone</b>. It fails closed. A typo is therefore an availability and
 * correctness bug — the feature becomes permanently unreachable for every tenant that paid for it —
 * and not a way past the gate.
 *
 * <p>That makes runtime validation the wrong tool: it would turn a startup-time typo into a runtime
 * exception on a path that already refuses. The gap worth closing is that <b>nothing tells anyone</b>
 * the identifier is wrong, so a typo ships and is discovered by a customer. This test is that
 * telling: it scans every production component and asserts each annotation value against
 * {@link EntitlementRegistry#ALL_MODULES}, the single canonical source.
 */
class RequireModuleIdentifierTest {

    private static final String PRODUCTION_PACKAGE = "com.hms";

    private final HospitalRepository hospitalRepository = mock(HospitalRepository.class);
    private final ModuleAccessAspect aspect = new ModuleAccessAspect();

    RequireModuleIdentifierTest() {
        ReflectionTestUtils.setField(aspect, "hospitalRepository", hospitalRepository);
    }

    @AfterEach void clear() { SecurityContextHolder.clearContext(); }

    // ── 1. What an unknown identifier does at runtime ─────────────────────────

    /** A deliberately misspelled module. TEST-ONLY — this never appears in production code. */
    @RequireModule("PHARAMCY")
    static class TypoGatedController {
        public void handler() { }
    }

    @RequireModule("PHARMACY")
    static class CorrectlyGatedController {
        public void handler() { }
    }

    @Test
    void anUnknownIdentifierFailsClosed_itIsNotAWayPastTheGate() throws Exception {
        // The tenant genuinely holds PHARMACY. The typo still denies, because "PHARAMCY" is not
        // in anybody's plan and never can be.
        hospitalRow(HospitalType.HOSPITAL, List.of("PHARMACY"));
        authenticate();

        assertThatThrownBy(() -> invoke(new TypoGatedController()))
                .as("a misspelled module must deny, never allow")
                .isInstanceOf(AccessDeniedException.class);

        // Same tenant, same plan, correct spelling: allowed. So the denial above is caused by the
        // typo alone — which is exactly the customer-visible outage a typo would ship.
        assertThatCode(() -> invoke(new CorrectlyGatedController())).doesNotThrowAnyException();
    }

    // ── 2. Every production annotation uses a canonical identifier ────────────

    @Test
    void everyProductionRequireModuleValueIsACanonicalModule() {
        List<String> problems = new ArrayList<>();
        Set<String> seen = new TreeSet<>();
        int annotations = 0;

        for (Class<?> type : productionComponents()) {
            RequireModule onClass = type.getAnnotation(RequireModule.class);
            if (onClass != null) {
                annotations++;
                seen.add(onClass.value());
                if (!EntitlementRegistry.ALL_MODULES.contains(onClass.value())) {
                    problems.add(type.getName() + " (class-level) -> '" + onClass.value() + "'");
                }
            }
            for (Method method : type.getDeclaredMethods()) {
                RequireModule onMethod = method.getAnnotation(RequireModule.class);
                if (onMethod == null) continue;
                annotations++;
                seen.add(onMethod.value());
                if (!EntitlementRegistry.ALL_MODULES.contains(onMethod.value())) {
                    problems.add(type.getName() + "#" + method.getName() + " -> '" + onMethod.value() + "'");
                }
            }
        }

        assertThat(problems)
                .as("@RequireModule values must exist in EntitlementRegistry.ALL_MODULES; "
                        + "an unknown one denies every tenant, permanently. Offenders: %s", problems)
                .isEmpty();
        assertThat(annotations)
                .as("the scan found no annotations at all, so it is proving nothing").isPositive();
        // Recorded so a reviewer can see what is actually gated, without a second hard-coded list.
        assertThat(seen).isSubsetOf(EntitlementRegistry.ALL_MODULES);
    }

    // ── 3. The check is falsifiable ───────────────────────────────────────────

    @Test
    void theValidationRejectsATypoAndAcceptsACanonicalValue() {
        // The same predicate the scan applies, exercised directly on both outcomes so a future
        // refactor cannot leave it vacuously true.
        assertThat(EntitlementRegistry.ALL_MODULES.contains(
                CorrectlyGatedController.class.getAnnotation(RequireModule.class).value()))
                .as("a canonical identifier must pass").isTrue();
        assertThat(EntitlementRegistry.ALL_MODULES.contains(
                TypoGatedController.class.getAnnotation(RequireModule.class).value()))
                .as("the misspelled identifier must be rejected by the same check").isFalse();
    }

    /**
     * The scan's detection power, shown rather than asserted in the abstract: the typo fixture in
     * this file IS found by the raw scanner and IS rejected by the predicate — it is excluded from
     * the production verdict only because it lives in test-classes.
     */
    @Test
    void aTypoIsDetectedByTheScan_andExcludedOnlyBecauseItIsTestCode() {
        Set<Class<?>> all = allScannedComponents();
        assertThat(all)
                .as("the raw scan must reach the typo fixture, or it could miss a real one")
                .contains(TypoGatedController.class);
        assertThat(EntitlementRegistry.ALL_MODULES
                .contains(TypoGatedController.class.getAnnotation(RequireModule.class).value()))
                .as("and the predicate must reject it").isFalse();
        assertThat(isProductionClass(TypoGatedController.class))
                .as("it is excluded from the production verdict for one reason only: it is test code")
                .isFalse();
        assertThat(productionComponents()).doesNotContain(TypoGatedController.class);
    }

    @Test
    void theScannerActuallyReachesTheGatedProductionControllers() {
        Set<String> gated = new TreeSet<>();
        for (Class<?> type : productionComponents()) {
            if (type.getAnnotation(RequireModule.class) != null) gated.add(type.getSimpleName());
        }
        // A scan that silently matched nothing would make the assertion above meaningless.
        assertThat(gated)
                .as("the class-level gates the audit recorded must be visible to this scan")
                .contains("SurgeryController", "IcuStayController", "InventoryController");
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    /**
     * Production classes only. Test classes share the {@code com.hms} package and are on the same
     * classpath — including this file's deliberate typo fixture — so they are filtered by where the
     * class was actually loaded from: {@code target/classes} is production, {@code target/test-classes}
     * is not. Without this, the scan reports its own fixture, which is how the exclusion was found.
     */
    private static boolean isProductionClass(Class<?> type) {
        var source = type.getProtectionDomain().getCodeSource();
        if (source == null || source.getLocation() == null) return false;
        String location = source.getLocation().getPath();
        return location.contains("/classes/") && !location.contains("/test-classes/");
    }

    private Set<Class<?>> allScannedComponents() {
        ClassPathScanningCandidateComponentProvider scanner =
                new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(RestController.class));
        scanner.addIncludeFilter(new AnnotationTypeFilter(Component.class));
        scanner.addIncludeFilter(new AnnotationTypeFilter(RequireModule.class));
        Set<Class<?>> classes = new LinkedHashSet<>();
        for (BeanDefinition bd : scanner.findCandidateComponents(PRODUCTION_PACKAGE)) {
            try {
                classes.add(Class.forName(bd.getBeanClassName()));
            } catch (ClassNotFoundException | NoClassDefFoundError e) {
                throw new IllegalStateException("Could not load " + bd.getBeanClassName(), e);
            }
        }
        return classes;
    }

    private Set<Class<?>> productionComponents() {
        Set<Class<?>> classes = new LinkedHashSet<>();
        for (Class<?> type : allScannedComponents()) {
            if (isProductionClass(type)) classes.add(type);
        }
        return classes;
    }

    private void hospitalRow(HospitalType type, List<String> modules) {
        Hospital hospital = new Hospital();
        hospital.setType(type);
        hospital.setModules(new ArrayList<>(modules));
        when(hospitalRepository.findById(anyLong())).thenReturn(Optional.of(hospital));
    }

    private void authenticate() {
        UserAuthenticationDetails details =
                new UserAuthenticationDetails(1L, "HOSPITAL_ADMIN", 7L, List.of());
        details.setHospitalType(HospitalType.HOSPITAL.name());
        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken("admin@tenant.test", null, List.of());
        auth.setDetails(details);
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    private void invoke(Object target) throws Exception {
        Method method = target.getClass().getMethod("handler");
        MethodSignature signature = mock(MethodSignature.class);
        when(signature.getMethod()).thenReturn(method);
        JoinPoint joinPoint = mock(JoinPoint.class);
        when(joinPoint.getSignature()).thenReturn(signature);
        when(joinPoint.getTarget()).thenReturn(target);
        aspect.checkModuleAccess(joinPoint);
    }
}
