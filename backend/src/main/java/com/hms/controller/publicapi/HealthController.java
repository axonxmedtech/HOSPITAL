package com.hms.controller.publicapi;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * HealthController - lightweight public health endpoint for uptime checks.
 *
 * This endpoint is intentionally public so external health checks (Render / UptimeRobot)
 * can access it without authentication. It returns a plain text response so
 * monitoring services can verify the backend is awake and responding.
 *
 * Why public:
 * - Render or UptimeRobot cannot present a JWT token when performing simple uptime probes.
 * - Allowing only this single path to be public keeps the rest of the API protected.
 *
 * How Render/UptimeRobot will use it:
 * - Periodically send `GET /api/public/health` and expect HTTP 200 with the body
 *   "HMS Backend Running" to consider the service healthy/awake.
 */
@RestController
public class HealthController {

    @GetMapping(value = "/api/public/health", produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> health() {
        // Return a simple, stable plain-text response so uptime checks are easy to assert.
        return ResponseEntity.ok("HMS Backend Running");
    }
}
