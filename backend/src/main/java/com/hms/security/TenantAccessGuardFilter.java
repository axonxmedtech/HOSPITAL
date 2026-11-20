package com.hms.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hms.service.subscription.TenantAccessService;
import com.hms.service.subscription.TenantAccessService.Access;
import com.hms.service.subscription.TenantAccessService.State;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Applies a tenant's access state (TenantAccessService) to every change it tries to make.
 *
 * <ul>
 *   <li><b>Demo ended</b>: every change is refused; reading still works, so the team can look.</li>
 *   <li><b>Plan expired</b> (after the grace days): only starting <em>new</em> work is refused —
 *       registering patients, opening OPD visits or appointments, admitting, adding staff or
 *       wards. Care of patients already in the hospital carries on.</li>
 * </ul>
 *
 * <p>Raising a support ticket and reading notifications always work, so a locked-out hospital can
 * still reach us. The Super Admin (no tenant) is never affected.
 */
@Component
public class TenantAccessGuardFilter extends OncePerRequestFilter {

    private static final Set<String> READ_METHODS = Set.of("GET", "HEAD", "OPTIONS");
    private static final Pattern TENANT_PREFIX = Pattern.compile("^/(hospital|clinic|pharmacy)(?=/)");

    /** Always allowed, even after a demo ends: asking for help, and housekeeping on one's own account. */
    private static final List<Pattern> ALWAYS_ALLOWED = List.of(
            Pattern.compile("^/tickets(/.*)?$"),
            Pattern.compile("^/notifications(/.*)?$"),
            Pattern.compile("^/help(/.*)?$"));
    private static final Set<String> ALWAYS_ALLOWED_EXACT = Set.of("/auth/profile", "/login");

    /** New work that stops once a plan has expired (POST, after the tenant prefix is removed). */
    private static final List<Pattern> NEW_WORK = List.of(
            Pattern.compile("^/patients(/import.*)?$"),
            Pattern.compile("^/opd$"),
            Pattern.compile("^/appointments$"),
            Pattern.compile("^/ipd/admit$"),
            Pattern.compile("^/(doctors|receptionists|nurses|pharmacists|ot-incharges)$"),
            Pattern.compile("^/(wards|icu/wards|ot/rooms|branches)$"));

    /** Looked up lazily: web-slice tests load filters without services, and must still start. */
    private final org.springframework.beans.factory.ObjectProvider<TenantAccessService> access;
    private final ObjectMapper mapper = new ObjectMapper();

    public TenantAccessGuardFilter(org.springframework.beans.factory.ObjectProvider<TenantAccessService> access) {
        this.access = access;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return READ_METHODS.contains(request.getMethod());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Long hospitalId = currentHospitalId();
        TenantAccessService service = access.getIfAvailable();
        if (hospitalId != null && service != null) {
            Access a = service.accessFor(hospitalId);
            String path = TENANT_PREFIX.matcher(pathOf(request)).replaceFirst("");
            if (a != null && refuses(a.state(), request.getMethod(), path)) {
                refuse(response, a);
                return;
            }
        }
        chain.doFilter(request, response);
    }

    /** The rule itself, separate from the servlet plumbing so it can be tested on its own. */
    static boolean refuses(State state, String method, String tenantPath) {
        if (READ_METHODS.contains(method)) return false;
        if (ALWAYS_ALLOWED_EXACT.contains(tenantPath)
                || ALWAYS_ALLOWED.stream().anyMatch(p -> p.matcher(tenantPath).matches())) {
            return false;
        }
        if (state == State.DEMO_ENDED) return true;
        return state == State.EXPIRED && "POST".equals(method)
                && NEW_WORK.stream().anyMatch(p -> p.matcher(tenantPath).matches());
    }

    private static String pathOf(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String ctx = request.getContextPath();
        return ctx != null && !ctx.isEmpty() && uri.startsWith(ctx) ? uri.substring(ctx.length()) : uri;
    }

    private static Long currentHospitalId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getDetails() instanceof UserAuthenticationDetails d)) return null;
        return d.getHospitalId();
    }

    private void refuse(HttpServletResponse response, Access a) throws IOException {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", false);
        body.put("error", a.message());
        body.put("code", a.state() == State.DEMO_ENDED ? "DEMO_ENDED" : "PLAN_EXPIRED");
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        mapper.writeValue(response.getOutputStream(), body);
    }
}
