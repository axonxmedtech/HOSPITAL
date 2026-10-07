package com.hms.security;

import com.hms.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * A deployed runtime refuses to start while an ACTIVE super admin holds a public credential, and
 * never says what that credential or its hash is. Which accounts count as active is the
 * repository's job and is proven against a real database in KnownDefaultCredentialGuardDatabaseTest.
 */
class KnownDefaultCredentialGuardTest {

    private static final PasswordEncoder ENCODER = new BCryptPasswordEncoder(4);
    private static final String DEFAULT_HASH = ENCODER.encode(KnownDefaultCredentials.DEVELOPMENT_SUPER_ADMIN_PASSWORD);
    private static final String ROTATED_HASH = ENCODER.encode("A-rotated-Passw0rd!"); // pragma: allowlist secret

    private final UserRepository users = mock(UserRepository.class);

    private static UserRepository.StoredCredential credential(String email, String hash) {
        return new UserRepository.StoredCredential() {
            public String getEmail() { return email; }
            public String getPasswordHash() { return hash; }
        };
    }

    private KnownDefaultCredentialGuard guard(String... profiles) {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles(profiles);
        return new KnownDefaultCredentialGuard(users, ENCODER, env);
    }

    private void activeSuperAdmins(UserRepository.StoredCredential... found) {
        when(users.findActiveSuperAdminCredentials()).thenReturn(Arrays.asList(found));
    }

    @Test
    void activeSuperAdminWithTheDevelopmentDefault_stagingRefusesToStart_namingOnlyTheEmail() {
        activeSuperAdmins(credential("legacy@example.com", DEFAULT_HASH));

        assertThatThrownBy(() -> guard("staging").verify())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("legacy@example.com")
                .hasMessageContaining("[staging]")
                .satisfies(e -> assertThat(e.getMessage())
                        .doesNotContain(KnownDefaultCredentials.DEVELOPMENT_SUPER_ADMIN_PASSWORD)
                        .doesNotContain(DEFAULT_HASH)
                        .doesNotContain("$2a$"));
    }

    @Test
    void productionAndUnknownProfilesRefuseToo() {
        activeSuperAdmins(credential("legacy@example.com", DEFAULT_HASH));

        for (String profile : List.of("prod", "production", "qa")) {
            assertThatThrownBy(() -> guard(profile).verify()).isInstanceOf(IllegalStateException.class);
        }
    }

    @Test
    void onlyTheExposedAccountsAreNamed() {
        activeSuperAdmins(credential("rotated@example.com", ROTATED_HASH),
                credential("legacy@example.com", DEFAULT_HASH));

        assertThatThrownBy(() -> guard("staging").verify())
                .hasMessageContaining("legacy@example.com")
                .satisfies(e -> assertThat(e.getMessage()).doesNotContain("rotated@example.com"));
    }

    @Test
    void activeSuperAdminWithARotatedPassword_starts() {
        activeSuperAdmins(credential("rotated@example.com", ROTATED_HASH));

        assertThatCode(() -> guard("staging").verify()).doesNotThrowAnyException();
    }

    @Test
    void noActiveSuperAdmin_starts() {
        activeSuperAdmins();

        assertThatCode(() -> guard("production").verify()).doesNotThrowAnyException();
    }

    @Test
    void developmentAndTestProfilesAreNotChecked() {
        for (String profile : List.of("default", "dev", "local", "test")) {
            assertThatCode(() -> guard(profile).verify()).doesNotThrowAnyException();
        }
        verify(users, never()).findActiveSuperAdminCredentials();
    }

    @Test
    void aMissingOrMalformedHashIsNotADefault() {
        activeSuperAdmins(credential("a@example.com", null), credential("b@example.com", ""),
                credential("c@example.com", "not-a-bcrypt-hash"));

        assertThatCode(() -> guard("staging").verify()).doesNotThrowAnyException();
    }

    // -------------------------------------------------------------- KnownDefaultCredentials

    @Test
    void anyHashOfTheDevelopmentPasswordIsKnown() {
        assertThat(KnownDefaultCredentials.isKnownDefault(ENCODER.encode(
                KnownDefaultCredentials.DEVELOPMENT_SUPER_ADMIN_PASSWORD), ENCODER)).isTrue();
        assertThat(KnownDefaultCredentials.isKnownDefault(ROTATED_HASH, ENCODER)).isFalse();
    }

    @Test
    void aPublishedHashIsKnownByExactDigest() {
        String published = ENCODER.encode("some-unknown-password"); // pragma: allowlist secret
        Set<String> digests = Set.of(KnownDefaultCredentials.sha256(published));

        assertThat(KnownDefaultCredentials.isKnownDefault(published, ENCODER, digests)).isTrue();
        assertThat(KnownDefaultCredentials.isKnownDefault(ROTATED_HASH, ENCODER, digests)).isFalse();
    }

    @Test
    void theRepositoryListsBothPublishedSetupHashes() {
        assertThat(KnownDefaultCredentials.PUBLISHED_HASH_DIGESTS)
                .hasSize(2)
                .allSatisfy(d -> assertThat(d).matches("[0-9a-f]{64}"));
    }
}
