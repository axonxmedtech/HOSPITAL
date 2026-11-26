package com.hms.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;

/**
 * Lets Prometheus scrape {@code /actuator/prometheus} with a static bearer token
 * ({@code METRICS_SCRAPE_TOKEN}, at least 32 characters), since a scraper cannot log in. The token
 * grants ROLE_METRICS_SCRAPER and nothing else: SecurityConfig allows that role on this one path,
 * so a leaked scrape token reads metrics and can reach no patient or tenant data.
 *
 * <p>Unset (or too short) means disabled -- the path is then Super Admin only. Compared in
 * constant time. Built in SecurityConfig (not a @Component) so it is not also auto-registered as a
 * plain servlet filter.
 */
public class MetricsScrapeAuthFilter extends OncePerRequestFilter {

    static final String PATH = "/actuator/prometheus";
    static final String ROLE = "ROLE_METRICS_SCRAPER";
    private static final int MIN_LENGTH = 32;

    private final byte[] token;

    public MetricsScrapeAuthFilter(String scrapeToken) {
        this.token = scrapeToken == null || scrapeToken.trim().length() < MIN_LENGTH
                ? null
                : scrapeToken.trim().getBytes(StandardCharsets.UTF_8);
    }

    public boolean isEnabled() {
        return token != null;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return token == null || !PATH.equals(request.getRequestURI());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")
                && MessageDigest.isEqual(header.substring(7).trim().getBytes(StandardCharsets.UTF_8), token)) {
            SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                    "metrics-scraper", null, List.of(new SimpleGrantedAuthority(ROLE))));
        }
        chain.doFilter(request, response);
    }
}
