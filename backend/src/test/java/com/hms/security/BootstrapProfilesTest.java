package com.hms.security;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The development credentials are allowlisted, not "everything but production": staging, unknown
 * and mixed profiles are deployed runtimes.
 */
class BootstrapProfilesTest {

    private static boolean development(String... active) {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles(active);
        return BootstrapProfiles.isDevelopmentOrTest(env);
    }

    @Test
    void noActiveProfileIsSpringsDefaultProfile_development() {
        assertThat(development()).isTrue();
    }

    @Test
    void explicitDevelopmentProfiles_development() {
        assertThat(development("default")).isTrue();
        assertThat(development("dev")).isTrue();
        assertThat(development("local")).isTrue();
        assertThat(development("test")).isTrue();
        assertThat(development("dev", "local")).isTrue();
    }

    @Test
    void stagingAndProduction_deployed() {
        assertThat(development("staging")).isFalse();
        assertThat(development("prod")).isFalse();
        assertThat(development("production")).isFalse();
    }

    @Test
    void unknownOrMisspeltProfiles_deployed() {
        assertThat(development("qa")).isFalse();
        assertThat(development("stagging")).isFalse();
        assertThat(development("preprod")).isFalse();
    }

    @Test
    void anyDeployedProfileInAMix_deployed() {
        assertThat(development("test", "staging")).isFalse();
        assertThat(development("dev", "prod")).isFalse();
    }

    @Test
    void aDefaultProfileOfStagingIsADeployedRuntime() {
        MockEnvironment env = new MockEnvironment();
        env.setDefaultProfiles("staging");
        assertThat(BootstrapProfiles.isDevelopmentOrTest(env)).isFalse();
    }
}
