package com.hms.component;

import com.hms.entity.User;
import com.hms.repository.UserRepository;
import com.hms.security.BootstrapProfiles;
import com.hms.security.KnownDefaultCredentials;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.env.Environment;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

/**
 * Creates the first super admin.
 *
 * <p>Development and test ({@link BootstrapProfiles}): the development super admin, with the
 * password in {@link KnownDefaultCredentials}, so a fresh clone and the test suite can log in.
 *
 * <p>Every other profile, staging and production included: never that account. A super admin is
 * created only when explicitly requested with {@code INITIAL_SUPER_ADMIN_EMAIL} and
 * {@code INITIAL_SUPER_ADMIN_PASSWORD}, only if no super admin exists yet, and only with a strong
 * password. Half a request, or a weak or public password, stops the startup rather than being
 * ignored.
 *
 * <p>No password is ever logged.
 */
@Component
public class DataInitializer implements CommandLineRunner {

    private static final Logger logger = LoggerFactory.getLogger(DataInitializer.class);
    private static final String SUPER_ADMIN = "SUPER_ADMIN";
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final Environment environment;
    private final String initialSuperAdminEmail;
    private final String initialSuperAdminPassword;

    public DataInitializer(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            Environment environment,
            @Value("${INITIAL_SUPER_ADMIN_EMAIL:}") String initialSuperAdminEmail,
            @Value("${INITIAL_SUPER_ADMIN_PASSWORD:}") String initialSuperAdminPassword) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.environment = environment;
        this.initialSuperAdminEmail = initialSuperAdminEmail;
        this.initialSuperAdminPassword = initialSuperAdminPassword;
    }

    @Override
    public void run(String... args) {
        if (BootstrapProfiles.isDevelopmentOrTest(environment)) {
            seedDevelopmentSuperAdmin();
        } else {
            bootstrapRequestedSuperAdmin();
        }
    }

    private void seedDevelopmentSuperAdmin() {
        try {
            if (userRepository.existsByEmail(KnownDefaultCredentials.DEVELOPMENT_SUPER_ADMIN_EMAIL)) {
                logger.info("[DataInitializer] Development Super Admin already exists — skipping seed");
                return;
            }
            createSuperAdmin(KnownDefaultCredentials.DEVELOPMENT_SUPER_ADMIN_EMAIL,
                    KnownDefaultCredentials.DEVELOPMENT_SUPER_ADMIN_PASSWORD);
            logger.info("[DataInitializer] Development Super Admin created: {} (development/test profiles only)",
                    KnownDefaultCredentials.DEVELOPMENT_SUPER_ADMIN_EMAIL);
        } catch (Exception e) {
            logger.error("[DataInitializer] ERROR seeding development Super Admin: {}", e.getClass().getSimpleName());
        }
    }

    private void bootstrapRequestedSuperAdmin() {
        boolean emailProvided = initialSuperAdminEmail != null && !initialSuperAdminEmail.isBlank();
        boolean passwordProvided = initialSuperAdminPassword != null && !initialSuperAdminPassword.isBlank();
        if (!emailProvided && !passwordProvided) {
            logger.info("[DataInitializer] {} profile: no Super Admin bootstrap requested; none created",
                    BootstrapProfiles.describe(environment));
            return;
        }
        if (!emailProvided || !passwordProvided) {
            throw new IllegalStateException(
                    "Initial Super Admin bootstrap requires both INITIAL_SUPER_ADMIN_EMAIL and INITIAL_SUPER_ADMIN_PASSWORD.");
        }
        String email = initialSuperAdminEmail.trim();
        validateBootstrap(email, initialSuperAdminPassword);
        if (userRepository.existsByRole(SUPER_ADMIN)) {
            logger.info("[DataInitializer] Super Admin already exists — skipping bootstrap");
            return;
        }
        if (userRepository.existsByEmail(email)) {
            throw new IllegalStateException("Initial Super Admin e-mail is already assigned to another user.");
        }
        createSuperAdmin(email, initialSuperAdminPassword);
        logger.info("[DataInitializer] Initial Super Admin created from explicit bootstrap configuration");
    }

    private void createSuperAdmin(String email, String rawPassword) {
        User superAdmin = new User();
        superAdmin.setEmail(email);
        superAdmin.setPassword(passwordEncoder.encode(rawPassword));
        superAdmin.setName("Super Admin");
        superAdmin.setRole(SUPER_ADMIN);
        superAdmin.setHospitalId(null);
        superAdmin.setIsActive(true);
        userRepository.save(superAdmin);
    }

    /** Package-private so the rules can be tested directly. */
    static void validateBootstrap(String email, String password) {
        if (!EMAIL_PATTERN.matcher(email).matches()) {
            throw new IllegalStateException("Initial Super Admin e-mail is invalid.");
        }
        if (KnownDefaultCredentials.DEVELOPMENT_SUPER_ADMIN_EMAIL.equalsIgnoreCase(email)
                || KnownDefaultCredentials.DEVELOPMENT_SUPER_ADMIN_PASSWORD.equals(password)) {
            throw new IllegalStateException("Initial Super Admin must not use the public development credential.");
        }
        if (password.length() < 12
                || password.chars().noneMatch(Character::isUpperCase)
                || password.chars().noneMatch(Character::isLowerCase)
                || password.chars().noneMatch(Character::isDigit)
                || password.chars().allMatch(Character::isLetterOrDigit)) {
            throw new IllegalStateException(
                    "Initial Super Admin password must be at least 12 characters and include upper-case, "
                            + "lower-case, digit and symbol characters.");
        }
    }
}
