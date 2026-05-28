package com.hms.config;

import com.hms.security.JwtAuthenticationFilter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import org.springframework.beans.factory.annotation.Value;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * SecurityConfig - Spring Security configuration
 * 
 * This configuration:
 * 1. Defines public endpoints (login endpoints)
 * 2. Protects platform and hospital endpoints
 * 3. Configures JWT authentication filter
 * 4. Enables CORS for frontend communication
 * 5. Disables session management (stateless JWT)
 * 
 * @author HMS Team
 * @version Phase-1
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    @Autowired
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @Value("${cors.allowed.origins}")
    private String allowedOrigins;

    /**
     * Configure HTTP security
     * 
     * @param http HttpSecurity object
     * @return SecurityFilterChain
     * @throws Exception if configuration fails
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                // Disable CSRF (not needed for stateless JWT)
                .csrf(csrf -> csrf.disable())

                // Enable CORS
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))

                // Configure authorization rules
                .authorizeHttpRequests(auth -> auth
                        // Public endpoints - no authentication required
                        .requestMatchers("/platform/login", "/login").permitAll()

                        // Public endpoints - no authentication required
                        .requestMatchers("/platform/login", "/login").permitAll()
                        .requestMatchers("/platform/users/debug-users").permitAll()
                        .requestMatchers("/api/public/health").permitAll()

                        // Platform endpoints - only Super Admin
                        .requestMatchers("/platform/**").hasRole("SUPER_ADMIN")

                        // WebSocket endpoints - authenticated standard HMS roles & Super Admin
                        .requestMatchers("/ws/**").hasAnyRole("HOSPITAL_ADMIN", "DOCTOR", "RECEPTIONIST", "PHARMACIST", "SUPER_ADMIN")

                        // Hospital and API endpoints - only standard HMS roles allowed
                        .requestMatchers("/hospital/**", "/api/pharmacy/**")
                        .hasAnyRole("HOSPITAL_ADMIN", "DOCTOR", "RECEPTIONIST", "PHARMACIST")
                        // All other requests require authentication
                        .anyRequest().authenticated())

                // Stateless session management (JWT-based)
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS))

                // Add JWT filter before UsernamePasswordAuthenticationFilter
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    /**
     * Configure CORS to allow frontend requests
     * 
     * @return CorsConfigurationSource
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();

        // Dynamically split by comma and trim to allow multiple origins
        List<String> origins = Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .collect(Collectors.toList());

        // Always ensure standard localhost is added for safety in dev, plus the production link
        if (!origins.contains("http://localhost:5173")) origins.add("http://localhost:5173");
        if (!origins.contains("http://localhost:3000")) origins.add("http://localhost:3000");

        configuration.setAllowedOrigins(origins);

        // Allow all HTTP methods
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));

        // Allow all headers
        configuration.setAllowedHeaders(Arrays.asList("*"));

        // Allow credentials (cookies, authorization headers)
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);

        return source;
    }

    /**
     * Password encoder bean for encrypting passwords
     * Uses BCrypt hashing algorithm
     * 
     * @return PasswordEncoder
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
