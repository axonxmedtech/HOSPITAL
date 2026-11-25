package com.hms.security;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Prometheus cannot log in, so it scrapes with a static token. The token must open the metrics
 * scrape and nothing else -- not /actuator/metrics, and no tenant endpoint.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.datasource.url=jdbc:h2:mem:scrape_token_test;MODE=MySQL;NON_KEYWORDS=VALUE;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
        "management.health.redis.enabled=false",
        "hms.metrics.scrape-token=" + MetricsScrapeTokenTest.TOKEN})
@ActiveProfiles("test")
class MetricsScrapeTokenTest {

    static final String TOKEN = "test-scrape-token-0123456789abcdef0123456789";

    @Autowired TestRestTemplate rest;

    private HttpStatusCode get(String path, String bearer) {
        HttpHeaders h = new HttpHeaders();
        if (bearer != null) h.setBearerAuth(bearer);
        return rest.exchange(path, HttpMethod.GET, new HttpEntity<>(h), String.class).getStatusCode();
    }

    @Test
    void theScrapeTokenOpensTheScrape() {
        assertThat(get("/actuator/prometheus", TOKEN)).isEqualTo(HttpStatus.OK);
    }

    @Test
    void aWrongTokenOrNoTokenIsRefused() {
        assertThat(get("/actuator/prometheus", TOKEN + "x")).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(get("/actuator/prometheus", null)).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void theScrapeTokenOpensNothingElse() {
        assertThat(get("/actuator/metrics", TOKEN)).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(get("/hospital/patients", TOKEN)).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(get("/platform/hospitals", TOKEN)).isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}
