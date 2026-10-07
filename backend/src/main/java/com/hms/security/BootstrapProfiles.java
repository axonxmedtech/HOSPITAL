package com.hms.security;

import org.springframework.core.env.Environment;

import java.util.Arrays;
import java.util.Locale;
import java.util.Set;

/**
 * Which runtimes may hold the known development credentials.
 *
 * <p>An allowlist, not "anything but production": staging, an unknown profile, a misspelt one or a
 * future environment must all be treated as deployed. The development super admin and its public
 * password exist only when every active profile is one of {@link #DEVELOPMENT_PROFILES}. With no
 * active profile, Spring's default profiles decide, so {@code spring.profiles.default=staging} is
 * still a deployed runtime.
 */
public final class BootstrapProfiles {

    /** Profiles that may seed the development super admin. */
    static final Set<String> DEVELOPMENT_PROFILES = Set.of("default", "dev", "local", "test");

    private BootstrapProfiles() {
    }

    /** True only when every active (or, with none active, default) profile is a development one. */
    public static boolean isDevelopmentOrTest(Environment environment) {
        String[] profiles = environment.getActiveProfiles();
        if (profiles.length == 0) {
            profiles = environment.getDefaultProfiles();
        }
        return profiles.length > 0 && Arrays.stream(profiles)
                .allMatch(p -> DEVELOPMENT_PROFILES.contains(p.trim().toLowerCase(Locale.ROOT)));
    }

    /** The profiles in effect, for messages. */
    public static String describe(Environment environment) {
        String[] profiles = environment.getActiveProfiles();
        return Arrays.toString(profiles.length > 0 ? profiles : environment.getDefaultProfiles());
    }
}
