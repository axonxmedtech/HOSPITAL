package com.hms.security;

import com.hms.config.StartupInitializationState;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.ResponseEntity;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * What a deployment can see over the public actuator endpoints, and only that.
 *
 * <p>Readiness is the gate a deploy waits on: it must follow startup initialisation, and a failure
 * must read as not-ready over HTTP, not just inside the JVM. Info must carry the exact 40-character
 * revision the deploy compares. Neither may expose components, details, configuration or secrets.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "management.health.redis.enabled=false")
@ActiveProfiles("test")
@DirtiesContext
class DeploymentReadinessEndpointTest {

    @Autowired TestRestTemplate rest;
    @Autowired StartupInitializationState state;

    @AfterEach
    void restore() {
        ReflectionTestUtils.setField(state, "phase", StartupInitializationState.Phase.COMPLETE);
    }

    private void assertMinimal(ResponseEntity<String> res) {
        assertThat(res.getBody())
                .doesNotContain("\"components\"")
                .doesNotContain("\"details\"")
                .doesNotContainIgnoringCase("jdbc")
                .doesNotContainIgnoringCase("password")
                .doesNotContainIgnoringCase("secret")
                .doesNotContain("icu_stay");
    }

    @Test
    void readinessIsUpWhenInitializationIsComplete() {
        ResponseEntity<String> res = rest.getForEntity("/actuator/health/readiness", String.class);

        assertThat(res.getStatusCode().value()).isEqualTo(200);
        assertThat(res.getBody()).contains("\"status\":\"UP\"");
        assertMinimal(res);
    }

    @Test
    void readinessIsOutOfServiceWhileInitializationIsStillRunning() {
        ReflectionTestUtils.setField(state, "phase", StartupInitializationState.Phase.STARTING);

        ResponseEntity<String> res = rest.getForEntity("/actuator/health/readiness", String.class);

        assertThat(res.getStatusCode().value()).isEqualTo(503);
        assertThat(res.getBody()).contains("\"status\":\"OUT_OF_SERVICE\"");
        assertMinimal(res);
    }

    @Test
    void readinessIsDownWhenInitializationFailed_withoutSayingWhyPublicly() {
        state.fail(List.of("missing required schema: icu_stay (id)"));

        ResponseEntity<String> res = rest.getForEntity("/actuator/health/readiness", String.class);

        assertThat(res.getStatusCode().value()).isEqualTo(503);
        assertThat(res.getBody()).contains("\"status\":\"DOWN\"");
        assertMinimal(res);
    }

    @Test
    void livenessDoesNotDependOnInitialization() {
        // A failed initialisation means "do not send traffic", not "restart the process".
        state.fail(List.of("missing required schema: icu_stay (id)"));

        ResponseEntity<String> res = rest.getForEntity("/actuator/health/liveness", String.class);

        assertThat(res.getStatusCode().value()).isEqualTo(200);
        assertThat(res.getBody()).contains("\"status\":\"UP\"");
        assertMinimal(res);
    }

    @Test
    void infoCarriesTheFullCommitIdAndNoConfiguration() {
        assumeTrue(new ClassPathResource("git.properties").exists(),
                "built without git metadata; nothing to expose");

        ResponseEntity<String> res = rest.getForEntity("/actuator/info", String.class);

        assertThat(res.getStatusCode().value()).isEqualTo(200);
        assertThat(res.getBody()).containsPattern("\"full\":\"[0-9a-f]{40}\"");
        assertThat(res.getBody())
                .doesNotContainIgnoringCase("jdbc")
                .doesNotContainIgnoringCase("password")
                .doesNotContainIgnoringCase("secret")
                .doesNotContain("\"env\"");
    }
}
