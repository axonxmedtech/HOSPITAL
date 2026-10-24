package com.hms.security;

import org.springframework.security.crypto.password.PasswordEncoder;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Set;

/**
 * Credentials that are public because this repository is.
 *
 * <p>Two kinds, both known to anyone with the source or its history:
 * <ul>
 *   <li>the development super admin's password, seeded only under {@link BootstrapProfiles}
 *       development profiles, checked with the {@link PasswordEncoder} so any hash of it is caught;</li>
 *   <li>BCrypt hashes that setup documentation once committed for a super admin. Anyone can attack
 *       those offline, so an account still holding one is treated as compromised. They are matched
 *       exactly, by SHA-256 digest, so the hashes themselves are not repeated here.</li>
 * </ul>
 */
public final class KnownDefaultCredentials {

    /** The development super admin. Seeded only in development and test profiles. */
    public static final String DEVELOPMENT_SUPER_ADMIN_EMAIL = "admin123@gmail.com";
    public static final String DEVELOPMENT_SUPER_ADMIN_PASSWORD = "pass123"; // pragma: allowlist secret

    /** SHA-256 (hex) of BCrypt hashes committed to this repository's setup documentation. */
    static final Set<String> PUBLISHED_HASH_DIGESTS = Set.of(
            "490cd67e7254a31730c1edd5d6f9ca41d2d788065110c1fcbd4f1693384fd7a7",    // setup/setup-super-admin.sql
            "702af6bfdf4f027954c2657f8ad381bc3faefa3d71cc58dcf0ebcb15bdb23f7d");  // README.md

    private KnownDefaultCredentials() {
    }

    /** True if this stored hash is, or verifies against, a publicly known credential. */
    public static boolean isKnownDefault(String storedHash, PasswordEncoder encoder) {
        return isKnownDefault(storedHash, encoder, PUBLISHED_HASH_DIGESTS);
    }

    /** Package-private so the digest path can be tested with a synthetic hash. */
    static boolean isKnownDefault(String storedHash, PasswordEncoder encoder, Set<String> publishedDigests) {
        if (storedHash == null || storedHash.isBlank()) {
            return false;
        }
        if (publishedDigests.contains(sha256(storedHash))) {
            return true;
        }
        try {
            return encoder.matches(DEVELOPMENT_SUPER_ADMIN_PASSWORD, storedHash);
        } catch (IllegalArgumentException malformedHash) {
            return false;
        }
    }

    static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }
}
