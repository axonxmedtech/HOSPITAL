package com.hms.component;

import com.hms.entity.User;
import com.hms.repository.UserRepository;
import com.hms.security.KnownDefaultCredentials;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The development super admin, whose password is public, exists only in development and test
 * profiles. Staging, production and any other profile get a super admin only from explicit, strong
 * bootstrap configuration, and no password is ever logged.
 */
@ExtendWith(OutputCaptureExtension.class)
class DataInitializerTest {

    private static final PasswordEncoder ENCODER = new BCryptPasswordEncoder(4);
    private static final String STRONG = "Bootstrap-Passw0rd!"; // pragma: allowlist secret

    private final UserRepository users = mock(UserRepository.class);

    private DataInitializer initializer(String email, String password, String... profiles) {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles(profiles);
        return new DataInitializer(users, ENCODER, env, email, password);
    }

    private User saved() {
        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(users).save(captor.capture());
        return captor.getValue();
    }

    // ------------------------------------------------------------- development / test

    @Test
    void developmentProfilesSeedTheDevelopmentSuperAdmin_withoutLoggingItsPassword(CapturedOutput output) {
        for (String[] profiles : List.of(new String[]{}, new String[]{"default"}, new String[]{"dev"},
                new String[]{"local"}, new String[]{"test"})) {
            UserRepository fresh = mock(UserRepository.class);
            MockEnvironment env = new MockEnvironment();
            env.setActiveProfiles(profiles);
            new DataInitializer(fresh, ENCODER, env, "", "").run();

            ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
            verify(fresh).save(captor.capture());
            User u = captor.getValue();
            assertThat(u.getEmail()).isEqualTo(KnownDefaultCredentials.DEVELOPMENT_SUPER_ADMIN_EMAIL);
            assertThat(u.getRole()).isEqualTo("SUPER_ADMIN");
            assertThat(ENCODER.matches(KnownDefaultCredentials.DEVELOPMENT_SUPER_ADMIN_PASSWORD, u.getPassword())).isTrue();
        }
        assertThat(output.getAll()).doesNotContain(KnownDefaultCredentials.DEVELOPMENT_SUPER_ADMIN_PASSWORD);
    }

    @Test
    void developmentSeedIsSkippedWhenTheAccountExists() {
        when(users.existsByEmail(KnownDefaultCredentials.DEVELOPMENT_SUPER_ADMIN_EMAIL)).thenReturn(true);

        initializer("", "", "test").run();

        verify(users, never()).save(any());
    }

    // ------------------------------------------------------------- deployed runtimes

    @Test
    void stagingProductionAndUnknownProfilesNeverCreateTheDevelopmentSuperAdmin() {
        for (String[] profiles : List.of(new String[]{"staging"}, new String[]{"prod"},
                new String[]{"production"}, new String[]{"qa"}, new String[]{"test", "staging"})) {
            initializer("", "", profiles).run();
        }
        verify(users, never()).save(any());
        verify(users, never()).existsByEmail(KnownDefaultCredentials.DEVELOPMENT_SUPER_ADMIN_EMAIL);
    }

    @Test
    void aDefaultProfileOfStagingIsADeployedRuntime() {
        MockEnvironment env = new MockEnvironment();
        env.setDefaultProfiles("staging");

        new DataInitializer(users, ENCODER, env, "", "").run();

        verify(users, never()).save(any());
    }

    @Test
    void explicitStrongBootstrapCreatesTheFirstSuperAdmin_withoutLoggingThePassword(CapturedOutput output) {
        initializer("owner@example.com", STRONG, "staging").run();

        User u = saved();
        assertThat(u.getEmail()).isEqualTo("owner@example.com");
        assertThat(u.getRole()).isEqualTo("SUPER_ADMIN");
        assertThat(u.getIsActive()).isTrue();
        assertThat(ENCODER.matches(STRONG, u.getPassword())).isTrue();
        assertThat(output.getAll()).doesNotContain(STRONG).doesNotContain(u.getPassword());
    }

    @Test
    void bootstrapIsSkippedWhenAnySuperAdminExists() {
        when(users.existsByRole("SUPER_ADMIN")).thenReturn(true);

        initializer("owner@example.com", STRONG, "production").run();

        verify(users, never()).save(any());
    }

    @Test
    void halfABootstrapRequestStopsTheStartup() {
        assertThatThrownBy(() -> initializer("owner@example.com", "", "staging").run())
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> initializer("", STRONG, "staging").run())
                .isInstanceOf(IllegalStateException.class);
        verify(users, never()).save(any());
    }

    @Test
    void weakOrPublicBootstrapCredentialsStopTheStartup_withoutEchoingThem() {
        for (String bad : List.of("short1!A", "alllowercase-123!", "ALLUPPERCASE-123!", "NoDigits-Here!",
                "NoSymbols12345", KnownDefaultCredentials.DEVELOPMENT_SUPER_ADMIN_PASSWORD)) {
            assertThatThrownBy(() -> initializer("owner@example.com", bad, "staging").run())
                    .isInstanceOf(IllegalStateException.class)
                    .satisfies(e -> assertThat(e.getMessage()).doesNotContain(bad));
        }
        assertThatThrownBy(() -> initializer(KnownDefaultCredentials.DEVELOPMENT_SUPER_ADMIN_EMAIL, STRONG,
                "staging").run()).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> initializer("not-an-email", STRONG, "staging").run())
                .isInstanceOf(IllegalStateException.class);
        verify(users, never()).save(any());
    }

    @Test
    void bootstrapRefusesAnEmailAlreadyHeldByAnotherUser() {
        when(users.existsByEmail(anyString())).thenReturn(true);

        assertThatThrownBy(() -> initializer("owner@example.com", STRONG, "staging").run())
                .isInstanceOf(IllegalStateException.class);
        verify(users, never()).save(any());
    }
}
