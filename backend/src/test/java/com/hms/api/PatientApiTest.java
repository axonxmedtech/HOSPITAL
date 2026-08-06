package com.hms.api;

import com.hms.entity.Hospital;
import com.hms.entity.ImportSource;
import com.hms.entity.Patient;
import com.hms.repository.HospitalRepository;
import com.hms.repository.PatientRepository;
import com.hms.security.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * API-level test for the business-critical **patient registration** workflow, driven over HTTP
 * against the real controller -> service -> repository stack (H2, fast tier). Covers success,
 * validation failure, retrieval, pagination and the auth boundary — success AND failure paths.
 *
 * Complements CrossTenantIsolationTest (which covers cross-tenant access) — here we prove the
 * happy path and input validation for a single tenant.
 *
 * <h2>Why the validation tests below matter more than they look</h2>
 * The Patient <em>entity</em> no longer validates name/gender/phone. Those rules used to live on
 * it as @NotBlank/@Pattern, but JPA runs Bean Validation on every persist, which made it
 * impossible for the legacy-data importer to keep a blank source value blank. The rules moved to
 * {@link com.hms.dto.PatientRequest}, bound by the create/update endpoints.
 *
 * The consequence: <strong>the controller is now the only thing standing between reception staff
 * and malformed patient data.</strong> Nothing downstream will catch a blank gender or a
 * "+919876543210" phone any more. If someone drops @Valid, swaps the DTO back to the entity, or
 * relaxes a rule on PatientRequest, these tests are the only alarm that will go off — every other
 * suite would stay green while daily registration silently started accepting junk.
 *
 * The mirror-image test, importerCanPersistPatientWithBlankGenderAndPhone, pins the other half of
 * the contract: the entity really must stay permissive, or the importer breaks.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class PatientApiTest {

    @Autowired TestRestTemplate rest;
    @Autowired JwtUtil jwtUtil;
    @Autowired HospitalRepository hospitalRepository;
    @Autowired PatientRepository patientRepository;

    private static final List<String> MODULES =
            List.of("OPD", "IPD", "PHARMACY", "BILLING", "NURSING", "APPOINTMENTS");

    private String token;
    private long hospitalId;

    @BeforeEach
    void setUp() {
        Hospital h = new Hospital();
        h.setName("API Test Hospital");
        h.setCustomId("HID-" + System.nanoTime());
        h.setSubscriptionStatus("ACTIVE");
        h.setIsActive(true);
        h.setModules(MODULES);
        h.setIsSingleDoctor(false);
        hospitalId = hospitalRepository.save(h).getId();
        token = jwtUtil.generateToken(1L, "admin@apitest.com", "HOSPITAL_ADMIN", hospitalId, MODULES, null, "HOSPITAL", null);
    }

    private HttpHeaders auth() {
        HttpHeaders h = new HttpHeaders();
        h.setBearerAuth(token);
        h.setContentType(MediaType.APPLICATION_JSON);
        return h;
    }

    @Test
    void registerPatient_validPayload_succeeds() {
        String body = "{\"name\":\"Ramesh Kumar\",\"dateOfBirth\":\"1980-06-15\",\"gender\":\"MALE\",\"phone\":\"9900112233\",\"address\":\"MG Road\"}";
        ResponseEntity<String> res = rest.exchange("/hospital/patients", HttpMethod.POST,
                new HttpEntity<>(body, auth()), String.class);

        assertThat(res.getStatusCode().is2xxSuccessful()).as("register should succeed").isTrue();
        assertThat(res.getBody()).contains("Ramesh Kumar").contains("publicId");
        // The BCrypt/secret fields must never be echoed back.
        assertThat(res.getBody()).doesNotContain("password");
    }

    @Test
    void registerPatient_missingDateOfBirth_returns400() {
        String body = "{\"name\":\"No DOB\",\"gender\":\"MALE\",\"phone\":\"9900112233\"}";
        ResponseEntity<String> res = rest.exchange("/hospital/patients", HttpMethod.POST,
                new HttpEntity<>(body, auth()), String.class);

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void listPatients_isPaginatedAndScopedToHospital() {
        // register two, then list
        for (String n : new String[] {"Pat One", "Pat Two"}) {
            rest.exchange("/hospital/patients", HttpMethod.POST, new HttpEntity<>(
                    "{\"name\":\"" + n + "\",\"dateOfBirth\":\"1990-01-01\",\"gender\":\"MALE\",\"phone\":\"9900000000\"}",
                    auth()), String.class);
        }
        ResponseEntity<String> res = rest.exchange("/hospital/patients?page=0&size=10", HttpMethod.GET,
                new HttpEntity<>(auth()), String.class);

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(res.getBody()).contains("\"totalElements\"").contains("Pat One");
    }

    @Test
    void patientsEndpoint_withoutToken_isUnauthorized() {
        ResponseEntity<String> res = rest.getForEntity("/hospital/patients", String.class);
        assertThat(res.getStatusCode()).isIn(HttpStatus.UNAUTHORIZED, HttpStatus.FORBIDDEN);
    }

    // ---------------------------------------------------------------------------------------
    // Manual-registration strictness. See the class comment: the entity stopped enforcing these,
    // so every assertion below is guarding a rule that has no second line of defence.
    // ---------------------------------------------------------------------------------------

    private ResponseEntity<String> register(String json) {
        return rest.exchange("/hospital/patients", HttpMethod.POST,
                new HttpEntity<>(json, auth()), String.class);
    }

    @Test
    void manualRegistrationStillRejectsBlankGender() {
        assertThat(register("{\"name\":\"Blank Gender\",\"dateOfBirth\":\"1990-01-01\",\"gender\":\"\","
                + "\"phone\":\"9900112233\"}").getStatusCode())
                .as("a blank gender must not reach the database via manual entry")
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void manualRegistrationStillRejectsMissingGender() {
        assertThat(register("{\"name\":\"No Gender\",\"dateOfBirth\":\"1990-01-01\","
                + "\"phone\":\"9900112233\"}").getStatusCode())
                .as("an absent gender key is as invalid as a blank one")
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void manualRegistrationStillRejectsNonTenDigitPhone() {
        // The exact shape legacy data arrives in (+91 prefix) — allowed for imports, never for
        // someone typing a patient in at the front desk.
        assertThat(register("{\"name\":\"Plus Prefix\",\"dateOfBirth\":\"1990-01-01\",\"gender\":\"MALE\","
                + "\"phone\":\"+919900112233\"}").getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void manualRegistrationStillRejectsBlankName() {
        assertThat(register("{\"name\":\"\",\"dateOfBirth\":\"1990-01-01\",\"gender\":\"MALE\","
                + "\"phone\":\"9900112233\"}").getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void manualRegistrationStillRejectsEmojiInName() {
        // @NoEmoji was copied from the entity onto PatientRequest precisely so this stays a 400
        // at the controller instead of blowing up later at persist time.
        assertThat(register("{\"name\":\"Ramesh \\uD83D\\uDE00\",\"dateOfBirth\":\"1990-01-01\","
                + "\"gender\":\"MALE\",\"phone\":\"9900112233\"}").getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    /** The full object the edit form sends back, including fields PatientRequest does not declare. */
    private static final String EDIT_FORM_PAYLOAD =
            "{\"name\":\"Edit Form\",\"dateOfBirth\":\"1990-01-01\",\"gender\":\"MALE\",\"phone\":\"9900112233\","
            + "\"id\":99,\"publicId\":\"client-supplied-uuid\",\"customId\":\"PAT9999\",\"status\":\"COMPLETED\","
            + "\"isActive\":false,\"age\":30,\"hospitalId\":4242,\"insurance\":\"NO\"}";

    @Test
    void editFormPayloadWithServerOwnedFieldsIsAcceptedNotRejected() {
        // PatientModal spreads the whole patient object into its payload, so unknown keys must be
        // ignored. This breaks the moment anyone enables Jackson's FAIL_ON_UNKNOWN_PROPERTIES —
        // see the note in application.properties.
        assertThat(register(EDIT_FORM_PAYLOAD).getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void clientSuppliedHospitalIdAndStatusAreIgnored() {
        // Ignoring these is a tenant-isolation guarantee, not a nicety: a client that could set
        // hospitalId would be writing into another hospital's records.
        String body = register(EDIT_FORM_PAYLOAD).getBody();

        assertThat(body).as("hospitalId must come from the JWT, never the payload")
                .contains("\"hospitalId\":" + hospitalId)
                .doesNotContain("4242");
        assertThat(body).as("a new patient is always REGISTERED and active")
                .contains("REGISTERED")
                .contains("\"isActive\":true");
        assertThat(body).as("publicId is server-generated")
                .doesNotContain("client-supplied-uuid");
    }

    // ---------------------------------------------------------------------------------------
    // The other half of the two-tier contract.
    // ---------------------------------------------------------------------------------------

    @Test
    void importerCanPersistPatientWithBlankGenderAndPhone() {
        // The whole reason the strict rules moved off the entity. Hibernate runs Bean Validation
        // on persist, so before that change this save threw ConstraintViolationException and the
        // "blank stays blank, no row is rejected" requirement was unimplementable. Saving outside
        // a transaction flushes immediately, so the validation listener really does run here.
        Patient legacy = new Patient();
        legacy.setHospitalId(hospitalId);
        legacy.setName("Legacy Record");
        legacy.setGender(null);
        legacy.setPhone(null);
        legacy.setDateOfBirth(null);
        legacy.setSource(ImportSource.IMPORTED);
        legacy.setLegacyId("OLD-4471");
        legacy.setImportBatchId(77L);
        legacy.setCustomFields("{\"old_ward\":\"B2\"}");

        Patient saved = patientRepository.save(legacy);
        assertThat(saved.getId()).isNotNull();

        Patient reloaded = patientRepository.findById(saved.getId()).orElseThrow();
        assertThat(reloaded.getGender()).as("a blank source value stays blank").isNull();
        assertThat(reloaded.getPhone()).as("a blank source value stays blank").isNull();
        assertThat(reloaded.getSource()).isEqualTo(ImportSource.IMPORTED);
        assertThat(reloaded.getLegacyId()).isEqualTo("OLD-4471");
        assertThat(reloaded.getImportBatchId()).isEqualTo(77L);
        assertThat(reloaded.getCustomFields()).contains("old_ward");
    }
}
