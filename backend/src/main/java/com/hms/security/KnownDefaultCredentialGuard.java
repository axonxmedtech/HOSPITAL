package com.hms.security;

import com.hms.repository.UserRepository;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Refuse to start a deployed runtime while an ACTIVE super admin holds a publicly known credential.
 *
 * <p>The development super admin used to be seeded in every profile with a password committed to
 * this public repository, and setup documentation committed super-admin password hashes. An active
 * account still holding one of those is a platform takeover waiting for a login, so outside the
 * {@link BootstrapProfiles} development profiles the context fails to refresh, before the web
 * server accepts a request.
 *
 * <p>Inactive accounts are deliberately not a failure. They cannot log in, and the authentication
 * filter refuses their tokens, so a deactivated legacy account that still stores the old default is
 * contained. Only active accounts are read.
 *
 * <p>Nothing here logs or throws a password or a hash: only e-mail addresses and counts.
 */
@Component
public class KnownDefaultCredentialGuard {

    private static final Logger log = LoggerFactory.getLogger(KnownDefaultCredentialGuard.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final Environment environment;

    public KnownDefaultCredentialGuard(UserRepository userRepository, PasswordEncoder passwordEncoder,
                                       Environment environment) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.environment = environment;
    }

    @PostConstruct
    public void verify() {
        if (BootstrapProfiles.isDevelopmentOrTest(environment)) {
            return;
        }
        List<String> exposed = userRepository.findActiveSuperAdminCredentials().stream()
                .filter(c -> KnownDefaultCredentials.isKnownDefault(c.getPasswordHash(), passwordEncoder))
                .map(UserRepository.StoredCredential::getEmail)
                .sorted()
                .toList();
        if (!exposed.isEmpty()) {
            throw new IllegalStateException("Refusing to start " + BootstrapProfiles.describe(environment)
                    + ": active SUPER_ADMIN account(s) " + exposed + " use a publicly known default "
                    + "credential. Change the password or deactivate the account before starting.");
        }
        log.info("Known-default credential check passed: no active super admin uses a public default.");
    }
}
