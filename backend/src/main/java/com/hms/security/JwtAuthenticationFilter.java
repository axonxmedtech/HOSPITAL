package com.hms.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * JwtAuthenticationFilter - Filter to validate JWT tokens and set security
 * context
 * 
 * This filter:
 * 1. Extracts JWT token from Authorization header
 * 2. Validates the token
 * 3. Extracts user information (userId, role, hospitalId)
 * 4. Sets Spring Security context for authorization
 * 
 * The hospitalId from the token is stored in the security context
 * and used by services to filter data for multi-tenant isolation.
 * 
 * @author HMS Team
 * @version Phase-1
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    @Autowired
    private JwtUtil jwtUtil;

    /**
     * Filter method to validate JWT and set security context
     * 
     * @param request     HTTP request
     * @param response    HTTP response
     * @param filterChain Filter chain
     * @throws ServletException if servlet error occurs
     * @throws IOException      if I/O error occurs
     */
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String authHeader = request.getHeader("Authorization");
        String token = null;

        // Check if Authorization header exists and starts with "Bearer "
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            // Extract token (remove "Bearer " prefix)
            token = authHeader.substring(7);
        } else {
            // Fall back to query parameter for WebSockets or secure file download/viewing endpoints
            token = request.getParameter("token");
        }

        if (token != null) {
            try {
                // Validate token
                if (jwtUtil.validateToken(token)) {
                    // Extract user information from token
                    String email = jwtUtil.extractEmail(token);
                    Long userId = jwtUtil.extractUserId(token);
                    String role = jwtUtil.extractRole(token);
                    Long hospitalId = jwtUtil.extractHospitalId(token);

                    // ROLE_<role> is unchanged: Clinic and Pharmacy authorize on it and must
                    // never see a permission. OT permissions are ADDED as plain authorities,
                    // so @PreAuthorize("hasAuthority('OT_SCHEDULE')") works with no custom
                    // PermissionEvaluator.
                    java.util.List<org.springframework.security.core.GrantedAuthority> authorities =
                            new java.util.ArrayList<>();
                    authorities.add(new SimpleGrantedAuthority("ROLE_" + role));
                    for (String permission : resolvePermissions(token, role)) {
                        authorities.add(new SimpleGrantedAuthority(permission));
                    }

                    UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                            email, null, authorities);

                    // Store additional details (userId, hospitalId, modules, branchId) in authentication
                    java.util.List<String> modules = jwtUtil.extractModules(token);
                    Long branchId = jwtUtil.extractBranchId(token);

                    // Multi Pharmacy branch impersonation for Hospital Admin
                    String branchHeader = request.getHeader("X-Branch-ID");
                    if (branchHeader != null && !branchHeader.trim().isEmpty() && "HOSPITAL_ADMIN".equals(role)) {
                        try {
                            branchId = Long.parseLong(branchHeader.trim());
                        } catch (NumberFormatException e) {
                            // ignore malformed branch header
                        }
                    }

                    UserAuthenticationDetails details = new UserAuthenticationDetails(userId, role, hospitalId,
                            modules);
                    details.setBranchId(branchId);
                    details.setHospitalType(jwtUtil.extractHospitalType(token));
                    authentication.setDetails(details);

                    // Set authentication in security context
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                }
            } catch (Exception e) {
                // Token validation failed - continue without authentication
                logger.error("JWT validation failed: " + e.getMessage());
            }
        }

        // Continue filter chain
        filterChain.doFilter(request, response);
    }

    /**
     * Permissions are minted into the token at login. A token issued before the claim
     * existed carries none, and revoking a live session's access mid-token would be a
     * regression, so fall back to the role's defaults for one release. An explicitly
     * empty list means the hospital granted this role nothing -- honour it.
     */
    private java.util.List<String> resolvePermissions(String token, String role) {
        java.util.List<String> claim = jwtUtil.extractPermissions(token);
        if (claim != null) return claim;
        return new java.util.ArrayList<>(OtPermissions.defaultsFor(role));
    }
}
