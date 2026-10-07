package com.hms.security;

import com.hms.entity.User;
import com.hms.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The guard against a real database, as a staging runtime would run it.
 *
 * <p>This is the state verified on staging before this guard shipped: the working super admin has a
 * rotated password, and the legacy development super admin still stores the public default but is
 * deactivated. That must start. Re-activating the legacy account must not.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class KnownDefaultCredentialGuardDatabaseTest {

    @Autowired UserRepository userRepository;
    @Autowired PasswordEncoder passwordEncoder;

    private KnownDefaultCredentialGuard stagingGuard;

    @BeforeEach
    void startFromNoActiveSuperAdmin() {
        // The test profile seeds the development super admin; other suites may add their own.
        // Deactivate every one inside this test's transaction, which is rolled back afterwards.
        userRepository.findAll().stream()
                .filter(u -> "SUPER_ADMIN".equals(u.getRole()))
                .forEach(u -> {
                    u.setIsActive(false);
                    userRepository.save(u);
                });
        MockEnvironment staging = new MockEnvironment();
        staging.setActiveProfiles("staging");
        stagingGuard = new KnownDefaultCredentialGuard(userRepository, passwordEncoder, staging);
    }

    private User superAdmin(String email, String rawPassword, boolean active) {
        User u = new User();
        u.setEmail(email);
        u.setPassword(passwordEncoder.encode(rawPassword));
        u.setName("Guard Test");
        u.setRole("SUPER_ADMIN");
        u.setIsActive(active);
        return userRepository.save(u);
    }

    @Test
    void rotatedActiveAdminAndInactiveLegacyDefault_startsAsVerifiedOnStaging() {
        superAdmin("guard-rotated-" + System.nanoTime() + "@t.test", "A-rotated-Passw0rd!", true); // pragma: allowlist secret
        superAdmin("guard-legacy-" + System.nanoTime() + "@t.test",
                KnownDefaultCredentials.DEVELOPMENT_SUPER_ADMIN_PASSWORD, false);

        assertThatCode(stagingGuard::verify).doesNotThrowAnyException();
    }

    @Test
    void reactivatingTheLegacyDefaultAccount_refusesToStart() {
        User legacy = superAdmin("guard-legacy-" + System.nanoTime() + "@t.test",
                KnownDefaultCredentials.DEVELOPMENT_SUPER_ADMIN_PASSWORD, false);
        legacy.setIsActive(true);
        userRepository.save(legacy);

        assertThatThrownBy(stagingGuard::verify)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(legacy.getEmail());
    }

    @Test
    void aNonSuperAdminWithTheDefaultIsNotThisGuardsConcern() {
        User staff = superAdmin("guard-staff-" + System.nanoTime() + "@t.test",
                KnownDefaultCredentials.DEVELOPMENT_SUPER_ADMIN_PASSWORD, true);
        staff.setRole("RECEPTIONIST");
        userRepository.save(staff);

        assertThatCode(stagingGuard::verify).doesNotThrowAnyException();
    }

    @Test
    void noSuperAdminAtAll_starts() {
        assertThatCode(stagingGuard::verify).doesNotThrowAnyException();
    }
}
