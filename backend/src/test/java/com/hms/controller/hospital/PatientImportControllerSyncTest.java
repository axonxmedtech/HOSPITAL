package com.hms.controller.hospital;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

import com.hms.entity.Hospital;
import com.hms.entity.import_.ImportStatus;
import com.hms.repository.HospitalRepository;
import com.hms.security.JwtUtil;
import com.hms.service.import_.AlreadyImportedException;
import com.hms.service.import_.ImportCommitSummary;
import com.hms.service.import_.ImportCounters;
import com.hms.service.import_.ImportEngine;
import com.hms.service.import_.ImportRunFailedException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

/**
 * With the engine mocked: the commit endpoint answers only after the engine returns (no 202, no
 * background thread), an infrastructure failure is a sanitised 5xx, and the spool is deleted on
 * the engine-exception paths too.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class PatientImportControllerSyncTest {

    private static Path spoolDir;

    @DynamicPropertySource
    static void spool(DynamicPropertyRegistry r) throws IOException {
        spoolDir = Files.createTempDirectory("hms-import-sync-test-");
        r.add("hms.import.spool-dir", () -> spoolDir.toString());
    }

    @Autowired TestRestTemplate rest;
    @Autowired JwtUtil jwt;
    @Autowired HospitalRepository hospitals;
    @MockBean ImportEngine engine;

    private String token;

    @BeforeEach
    void tenant() {
        Hospital h = new Hospital();
        h.setName("Sync " + System.nanoTime());
        h.setCustomId("HID-" + System.nanoTime());
        h.setSubscriptionStatus("ACTIVE");
        h.setIsActive(true);
        h.setModules(List.of("OPD"));
        h.setIsSingleDoctor(false);
        long id = hospitals.save(h).getId();
        token = jwt.generateToken(1L, "admin@sync.test", "HOSPITAL_ADMIN", id, List.of("OPD"), null, "HOSPITAL", null);
    }

    private ResponseEntity<String> commit() {
        MultiValueMap<String, Object> form = new LinkedMultiValueMap<>();
        form.add("file", new ByteArrayResource("Name,Phone\nA,9000000001\n".getBytes(StandardCharsets.UTF_8)) {
            @Override public String getFilename() { return "a.csv"; }
        });
        form.add("mapping", "{\"Name\":\"name\",\"Phone\":\"phone\"}");
        HttpHeaders h = new HttpHeaders();
        h.setBearerAuth(token);
        h.setContentType(MediaType.MULTIPART_FORM_DATA);
        return rest.exchange("/hospital/patients/import/commit", HttpMethod.POST, new HttpEntity<>(form, h), String.class);
    }

    private long spoolFiles() throws IOException {
        try (Stream<Path> s = Files.list(spoolDir)) { return s.count(); }
    }

    @Test
    void commitDoesNotRespondUntilTheEngineHasFinished() throws Exception {
        CountDownLatch engineEntered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        ImportCommitSummary summary = new ImportCommitSummary("done-id", ImportStatus.COMPLETED, new ImportCounters.Snapshot(1, 1, 0, 0, 0, 0), LocalDateTime.of(2026, 9, 18, 12, 0));
        doAnswer(inv -> { engineEntered.countDown(); release.await(30, TimeUnit.SECONDS); return summary; }).when(engine).commit(any(), any(), any());

        ExecutorService pool = Executors.newSingleThreadExecutor();
        try {
            Future<ResponseEntity<String>> response = pool.submit(this::commit);
            assertThat(engineEntered.await(30, TimeUnit.SECONDS)).isTrue();
            Thread.sleep(300);
            assertThat(response.isDone()).as("the HTTP response must wait for the engine").isFalse();
            release.countDown();
            ResponseEntity<String> r = response.get(30, TimeUnit.SECONDS);
            assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(r.getBody()).contains("done-id").contains("COMPLETED");
        } finally {
            release.countDown();
            pool.shutdownNow();
        }
        assertThat(spoolFiles()).isZero();
    }

    @Test
    void anInfrastructureFailureIsASanitised5xxAndTheSpoolIsGone() throws Exception {
        when(engine.commit(any(), any(), any())).thenThrow(new ImportRunFailedException("batch-x", new org.springframework.dao.CannotAcquireLockException("Deadlock found when trying to get lock; SELECT * FROM patients")));

        ResponseEntity<String> r = commit();

        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(r.getBody()).contains("batch-x").doesNotContain("Deadlock").doesNotContain("SELECT").doesNotContain("CannotAcquireLock").doesNotContain("Exception");
        assertThat(spoolFiles()).isZero();
    }

    @Test
    void anUnresolvedConcurrentImportIs409WithoutAnyBatchId() throws Exception {
        when(engine.commit(any(), any(), any())).thenThrow(AlreadyImportedException.inProgressButNotYetVisible());

        ResponseEntity<String> r = commit();

        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(r.getBody()).contains("IMPORT_ALREADY_IN_PROGRESS").contains("\"detailsAvailable\":\"false\"").contains("\"status\":\"RUNNING\"")
                .doesNotContain("batchPublicId").doesNotContain("unknown").doesNotContain("committedAt");
        assertThat(spoolFiles()).isZero();
    }
}
