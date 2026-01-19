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
import java.util.Collections;

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

        // Extract Authorization header
        String authHeader = request.getHeader("Authorization");

        // Check if Authorization header exists and starts with "Bearer "
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            // Extract token (remove "Bearer " prefix)
            String token = authHeader.substring(7);

            try {
                // Validate token
                if (jwtUtil.validateToken(token)) {
                    // Extract user information from token
                    String email = jwtUtil.extractEmail(token);
                    Long userId = jwtUtil.extractUserId(token);
                    String role = jwtUtil.extractRole(token);
                    Long hospitalId = jwtUtil.extractHospitalId(token);

                    // Create authentication object with role
                    UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                            email,
                            null,
                            Collections.singletonList(new SimpleGrantedAuthority("ROLE_" + role)));

                    // Store additional details (userId, hospitalId, modules) in authentication
                    java.util.List<String> modules = jwtUtil.extractModules(token);
                    UserAuthenticationDetails details = new UserAuthenticationDetails(userId, role, hospitalId,
                            modules);
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
}
