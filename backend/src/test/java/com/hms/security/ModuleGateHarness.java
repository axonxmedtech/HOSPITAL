package com.hms.security;

import com.hms.entity.Hospital;
import com.hms.entity.HospitalType;
import com.hms.repository.HospitalRepository;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Shared harness for the module-gate tests, extracted so OPD, IPD and PHARMACY each read as a
 * statement about their own boundary rather than three copies of the same plumbing. It follows
 * {@link AppointmentModuleGateTest}'s approach exactly: drive the real {@link ModuleAccessAspect}
 * against the real controller class, and enumerate handlers by reflection so a mutation added
 * later without its annotation fails a test the moment it is written.
 *
 * <p>The hospital row is a mock, which is what makes the revocation case honest: the modules the
 * row returns can be changed <i>after</i> the principal is built, reproducing "the plan changed
 * while this user was signed in" without minting a second token.
 */
final class ModuleGateHarness {

    private final HospitalRepository hospitalRepository = mock(HospitalRepository.class);
    final ModuleAccessAspect aspect = new ModuleAccessAspect();

    ModuleGateHarness() {
        ReflectionTestUtils.setField(aspect, "hospitalRepository", hospitalRepository);
    }

    /**
     * Authenticate a tenant of the given type whose live row holds {@code modules}.
     *
     * <p>The type is written to the row as well as the token: since S-SEC-3C the row is what
     * decides whether the gate runs, so a test about clinic or pharmacy behaviour that only set
     * the claim would be testing nothing.
     */
    void tenant(HospitalType type, List<String> modules) {
        liveRowHolds(type, modules);
        UserAuthenticationDetails details =
                new UserAuthenticationDetails(1L, "HOSPITAL_ADMIN", 7L, new ArrayList<>(modules));
        details.setHospitalType(type == null ? null : type.name());
        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken("admin@tenant.test", null, List.of());
        auth.setDetails(details);
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    /**
     * Rewrite ONLY the authoritative hospital row, leaving the already-built principal (and its
     * stale module claim) exactly as it was. This is the mid-session revocation scenario.
     */
    void liveRowHolds(List<String> modules) {
        liveRowHolds(HospitalType.HOSPITAL, modules);
    }

    void liveRowHolds(HospitalType type, List<String> modules) {
        Hospital hospital = new Hospital();
        hospital.setType(type);
        hospital.setModules(new ArrayList<>(modules));
        when(hospitalRepository.findById(anyLong())).thenReturn(Optional.of(hospital));
    }

    /**
     * The aspect resolves a class-level annotation through {@code joinPoint.getTarget().getClass()},
     * so a target instance is required. These controllers take constructor-injected dependencies,
     * and Mockito builds an instance without invoking any constructor — the annotation is still
     * found, because Spring's findMergedAnnotation walks the superclass chain.
     */
    JoinPoint joinPointFor(Method method, Class<?> targetType) {
        MethodSignature signature = mock(MethodSignature.class);
        when(signature.getMethod()).thenReturn(method);
        JoinPoint joinPoint = mock(JoinPoint.class);
        when(joinPoint.getSignature()).thenReturn(signature);
        when(joinPoint.getTarget()).thenReturn(mock(targetType));
        return joinPoint;
    }

    static boolean isMutation(Method m) {
        return m.isAnnotationPresent(PostMapping.class)
                || m.isAnnotationPresent(PutMapping.class)
                || m.isAnnotationPresent(PatchMapping.class)
                || m.isAnnotationPresent(DeleteMapping.class);
    }

    static boolean isRead(Method m) {
        return m.isAnnotationPresent(GetMapping.class);
    }

    static Method handler(Class<?> controller, String name) {
        for (Method m : controller.getDeclaredMethods()) {
            if (m.getName().equals(name)) return m;
        }
        throw new IllegalArgumentException("No handler named " + name + " on " + controller.getSimpleName());
    }

    static void clear() {
        SecurityContextHolder.clearContext();
    }
}
