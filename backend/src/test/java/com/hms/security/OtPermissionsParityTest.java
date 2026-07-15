package com.hms.security;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.type.filter.AssignableTypeFilter;
import org.springframework.security.access.prepost.PreAuthorize;

import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phase 2's exit criterion: swapping OT authorization from hasRole(...) to
 * hasAuthority('OT_*') must not change who can reach which endpoint on day one.
 *
 * The table below is the PRE-refactor access, transcribed from the @PreAuthorize
 * annotations as they stood at 408f69d. This test derives post-refactor access from
 * the live annotations plus OtPermissions.DEFAULTS and asserts the two agree.
 *
 * If this fails, either a default changed or an endpoint's permission is wrong.
 */
class OtPermissionsParityTest {

    private static final Pattern AUTHORITY = Pattern.compile("hasAuthority\\('([A-Z_]+)'\\)");

    /** Roles that could reach each OT endpoint BEFORE the permission layer. */
    private static final Map<String, Set<String>> PRE_REFACTOR_ACCESS = new LinkedHashMap<>();
    static {
        // SurgeryController
        PRE_REFACTOR_ACCESS.put("SurgeryController#create", Set.of("DOCTOR"));
        PRE_REFACTOR_ACCESS.put("SurgeryController#activeForAdmission",
                Set.of("DOCTOR", "RECEPTIONIST", "NURSE", "HOSPITAL_ADMIN"));
        PRE_REFACTOR_ACCESS.put("SurgeryController#requests", Set.of("RECEPTIONIST"));
        PRE_REFACTOR_ACCESS.put("SurgeryController#board", Set.of("RECEPTIONIST"));
        PRE_REFACTOR_ACCESS.put("SurgeryController#myBoard", Set.of("DOCTOR"));
        PRE_REFACTOR_ACCESS.put("SurgeryController#surgeons", Set.of("RECEPTIONIST"));
        PRE_REFACTOR_ACCESS.put("SurgeryController#schedule", Set.of("RECEPTIONIST"));
        PRE_REFACTOR_ACCESS.put("SurgeryController#start", Set.of("RECEPTIONIST"));
        PRE_REFACTOR_ACCESS.put("SurgeryController#complete", Set.of("RECEPTIONIST"));
        PRE_REFACTOR_ACCESS.put("SurgeryController#cancel", Set.of("RECEPTIONIST"));
        // SurgeryFormController
        PRE_REFACTOR_ACCESS.put("SurgeryFormController#save", Set.of("NURSE"));
        PRE_REFACTOR_ACCESS.put("SurgeryFormController#get", Set.of("NURSE", "DOCTOR", "HOSPITAL_ADMIN"));
        PRE_REFACTOR_ACCESS.put("SurgeryFormController#listSaved", Set.of("NURSE", "DOCTOR", "HOSPITAL_ADMIN"));
    }

    /** Endpoint -> the permission its @PreAuthorize now demands. */
    private Map<String, String> currentPermissions() throws Exception {
        Map<String, String> out = new LinkedHashMap<>();
        for (String simpleName : Set.of("SurgeryController", "SurgeryFormController")) {
            Class<?> type = Class.forName("com.hms.controller.hospital." + simpleName);
            for (Method m : type.getDeclaredMethods()) {
                PreAuthorize pa = AnnotatedElementUtils.findMergedAnnotation(m, PreAuthorize.class);
                if (pa == null) continue;
                Matcher matcher = AUTHORITY.matcher(pa.value());
                assertThat(matcher.find())
                        .as("%s#%s still authorizes on a role, not a permission: %s",
                                simpleName, m.getName(), pa.value())
                        .isTrue();
                out.put(simpleName + "#" + m.getName(), matcher.group(1));
            }
        }
        return out;
    }

    private Set<String> rolesHolding(String permission) {
        Set<String> roles = new TreeSet<>();
        for (String role : OtPermissions.ROLES) {
            if (OtPermissions.defaultsFor(role).contains(permission)) roles.add(role);
        }
        return roles;
    }

    @Test
    void everyOtEndpoint_authorizesOnAPermission_notARole() throws Exception {
        assertThat(currentPermissions()).isNotEmpty();
    }

    @Test
    void dayOneAccess_isIdenticalToThePreRefactorRoleChecks() throws Exception {
        Map<String, String> permissions = currentPermissions();
        Map<String, Set<String>> mismatches = new LinkedHashMap<>();

        for (Map.Entry<String, Set<String>> expected : PRE_REFACTOR_ACCESS.entrySet()) {
            String endpoint = expected.getKey();
            String permission = permissions.get(endpoint);
            assertThat(permission).as("no @PreAuthorize found for %s", endpoint).isNotNull();

            Set<String> now = rolesHolding(permission);
            Set<String> before = new TreeSet<>(expected.getValue());
            if (!now.equals(before)) {
                mismatches.put(endpoint + " (" + permission + ")", diff(before, now));
            }
        }
        assertThat(mismatches)
                .as("Access changed for these endpoints. Day-1 behaviour must be identical; "
                        + "a hospital opts into anything different via the permission matrix.")
                .isEmpty();
    }

    private Set<String> diff(Set<String> before, Set<String> now) {
        Set<String> out = new LinkedHashSet<>();
        for (String r : now) if (!before.contains(r)) out.add("+" + r);
        for (String r : before) if (!now.contains(r)) out.add("-" + r);
        return out;
    }

    /** Nobody should be able to grant a code that no endpoint recognises. */
    @Test
    void everyDefaultedPermission_isAValidCode() {
        for (String role : OtPermissions.ROLES) {
            for (String code : OtPermissions.defaultsFor(role)) {
                assertThat(OtPermissions.isValid(code))
                        .as("%s defaults to unknown permission %s", role, code).isTrue();
            }
        }
    }

    /** A hospital admin must always retain the ability to reach the matrix itself. */
    @Test
    void hospitalAdmin_holdsOtSettingsByDefault() {
        assertThat(OtPermissions.defaultsFor("HOSPITAL_ADMIN")).contains(OtPermissions.OT_SETTINGS);
    }
}
