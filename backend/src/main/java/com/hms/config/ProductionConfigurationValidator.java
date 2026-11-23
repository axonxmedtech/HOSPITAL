package com.hms.config;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Refuse to start a production instance that is configured unsafely.
 *
 * <p>{@code application.properties} ships working defaults so a developer can clone and run:
 * {@code jwt.secret} falls back to a placeholder that is committed to this repository, and
 * {@code cors.allowed.origins} falls back to {@code http://localhost:5173}. Both are correct for
 * development and catastrophic in production — the first lets anyone holding this public repo mint
 * a valid token for any hospital, and the second either breaks the real frontend or, if someone
 * "fixes" it by widening the origin, opens the API to a page served over plain HTTP.
 *
 * <p>Nothing checked either. The failure mode is silent: the application starts, serves traffic,
 * and looks healthy. This turns that into a startup that stops, which is the only outcome that
 * cannot be missed.
 *
 * <p>Production only, using the same profile test the security and websocket configuration
 * already use ({@code prod} or {@code production}). Development, test and staging are untouched
 * deliberately — staging runs against real infrastructure but is not the boundary this protects,
 * and breaking every developer's first run would get the check deleted rather than fixed.
 *
 * <p>Runs at construction rather than on {@code ApplicationReadyEvent} so the context fails to
 * refresh. A ready-event check runs after the server is already accepting requests, which for a
 * credential problem is too late to be worth much.
 *
 * <p>No message here contains a secret, only a description of what is wrong with it.
 */
@Component
public class ProductionConfigurationValidator {

    private static final Logger logger = LoggerFactory.getLogger(ProductionConfigurationValidator.class);

    /** The placeholder committed in application.properties. Public knowledge by definition. */
    static final String DEFAULT_JWT_SECRET = "YOUR_SECRET_KEY_HERE_MUST_BE_VERY_LONG_FOR_SECURITY";

    /**
     * HS256 keys shorter than this are rejected outright by the JJWT signer, and a shorter value
     * is a weak key regardless of what any library accepts.
     */
    static final int MIN_JWT_SECRET_BYTES = 32;

    private static final Set<String> WEAK_SECRETS = Set.of(
            "changeme", "change-me", "secret", "jwt_secret", "jwt-secret",
            "your-secret", "password", "test", "dev");

    private final Environment environment;
    private final String jwtSecret;
    private final String allowedOrigins;
    private final String datasourceUrl;

    public ProductionConfigurationValidator(
            Environment environment,
            @Value("${jwt.secret:}") String jwtSecret,
            @Value("${cors.allowed.origins:}") String allowedOrigins,
            @Value("${spring.datasource.url:}") String datasourceUrl) {
        this.environment = environment;
        this.jwtSecret = jwtSecret;
        this.allowedOrigins = allowedOrigins;
        this.datasourceUrl = datasourceUrl;
    }

    @PostConstruct
    public void validate() {
        if (!isProduction()) {
            return;
        }
        validateJwtSecret(jwtSecret);
        validateFrontendOrigins(allowedOrigins);
        String transportProblem = plaintextRemoteDatabase(datasourceUrl);
        if (transportProblem != null) {
            logger.error("SECURITY: {}", transportProblem);
        }
        logger.info("Production configuration validated: JWT secret and frontend origins are acceptable.");
    }

    /** Package-private so the rules can be tested without booting a production context. */
    static void validateJwtSecret(String secret) {
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException(
                    "JWT_SECRET is not set. Production requires an explicit signing secret of at least "
                            + MIN_JWT_SECRET_BYTES + " characters.");
        }
        if (DEFAULT_JWT_SECRET.equals(secret)) {
            throw new IllegalStateException(
                    "JWT_SECRET is still the placeholder committed in application.properties. "
                            + "Anyone with the source can forge tokens for any hospital. Set a real secret.");
        }
        if (WEAK_SECRETS.contains(secret.trim().toLowerCase(Locale.ROOT))) {
            throw new IllegalStateException("JWT_SECRET is a well-known placeholder value. Set a real secret.");
        }
        if (secret.length() < MIN_JWT_SECRET_BYTES) {
            // The length is not a secret; the value is. Naming it helps the operator fix this.
            throw new IllegalStateException(
                    "JWT_SECRET is " + secret.length() + " characters; production requires at least "
                            + MIN_JWT_SECRET_BYTES + ".");
        }
    }

    /** Package-private for the same reason. */
    static void validateFrontendOrigins(String origins) {
        if (origins == null || origins.isBlank()) {
            throw new IllegalStateException(
                    "FRONTEND_URL is not set. Production requires an explicit HTTPS frontend origin.");
        }
        List<String> values = Arrays.stream(origins.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
        if (values.isEmpty()) {
            throw new IllegalStateException("FRONTEND_URL is not set. Production requires an HTTPS frontend origin.");
        }
        for (String origin : values) {
            String lower = origin.toLowerCase(Locale.ROOT);
            if ("*".equals(origin)) {
                throw new IllegalStateException(
                        "FRONTEND_URL is '*'. A wildcard origin lets any site call this API with credentials.");
            }
            if (!lower.startsWith("https://")) {
                throw new IllegalStateException(
                        "FRONTEND_URL origin '" + origin + "' is not HTTPS. Production must not accept a "
                                + "plain-HTTP origin: tokens would travel in clear text.");
            }
            if (isLocal(lower)) {
                throw new IllegalStateException(
                        "FRONTEND_URL origin '" + origin + "' points at the local machine. That is a "
                                + "development value and cannot be the production frontend.");
            }
        }
    }

    /**
     * Patient data must not cross a network unencrypted. A database on this machine (localhost) or
     * on the Docker network (a single-label host such as {@code db}) never leaves the server, so
     * {@code useSSL=false} is acceptable there. Anywhere else it is not.
     *
     * <p>Reported, not enforced: refusing to start would turn a configuration we cannot see from
     * here into a failed deploy. Package-private for tests.
     *
     * @return a description of the problem, or null when the connection is acceptable
     */
    static String plaintextRemoteDatabase(String jdbcUrl) {
        if (jdbcUrl == null || !jdbcUrl.startsWith("jdbc:mysql://")) {
            return null;
        }
        String rest = jdbcUrl.substring("jdbc:mysql://".length());
        int end = rest.length();
        for (char c : new char[] {'/', '?'}) {
            int i = rest.indexOf(c);
            if (i >= 0 && i < end) end = i;
        }
        String host = rest.substring(0, end).toLowerCase(Locale.ROOT);
        if (host.startsWith("[")) {
            host = host.substring(1, Math.max(1, host.indexOf(']')));
        } else if (host.contains(":")) {
            host = host.substring(0, host.indexOf(':'));
        }
        boolean sameServer = host.isEmpty() || "localhost".equals(host) || host.startsWith("127.")
                || "::1".equals(host) || (!host.contains(".") && !host.contains(":"));
        if (sameServer) {
            return null;
        }
        String params = jdbcUrl.contains("?") ? jdbcUrl.substring(jdbcUrl.indexOf('?') + 1).toLowerCase(Locale.ROOT) : "";
        boolean encrypted = params.contains("sslmode=required") || params.contains("sslmode=verify_ca")
                || params.contains("sslmode=verify_identity")
                || (params.contains("usessl=true") && params.contains("requiressl=true"));
        if (encrypted) {
            return null;
        }
        return "the database at '" + host + "' is on another machine but the connection is not "
                + "encrypted. Patient data crosses the network in clear text. Add sslMode=REQUIRED "
                + "(or VERIFY_IDENTITY) to SPRING_DATASOURCE_URL.";
    }

    private static boolean isLocal(String lowerOrigin) {
        String host = lowerOrigin.substring("https://".length());
        int slash = host.indexOf('/');
        if (slash >= 0) host = host.substring(0, slash);
        int colon = host.indexOf(':');
        if (colon >= 0) host = host.substring(0, colon);
        return host.equals("localhost")
                || host.equals("127.0.0.1")
                || host.equals("0.0.0.0")
                || host.equals("::1")
                || host.endsWith(".local");
    }

    /** Same profile test SecurityConfig and WebSocketConfig already use. */
    private boolean isProduction() {
        return Arrays.stream(environment.getActiveProfiles())
                .anyMatch(p -> "prod".equalsIgnoreCase(p) || "production".equalsIgnoreCase(p));
    }
}
