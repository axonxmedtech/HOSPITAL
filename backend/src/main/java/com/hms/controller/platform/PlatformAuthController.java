package com.hms.controller.platform;

import com.hms.dto.LoginRequest;
import com.hms.dto.LoginResponse;
import com.hms.service.platform.PlatformAuthService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * PlatformAuthController - REST controller for Super Admin authentication
 * 
 * This controller handles Super Admin login at /platform/login.
 * This is a separate login endpoint from hospital users.
 * 
 * @author HMS Team
 * @version Phase-1
 */
@RestController
@RequestMapping("/platform")
@CrossOrigin(origins = { "http://localhost:3000", "http://localhost:5173" })
public class PlatformAuthController {

    @Autowired
    private PlatformAuthService authService;

    /**
     * Super Admin login endpoint
     * 
     * @param request LoginRequest containing email and password
     * @return LoginResponse with JWT token and user details
     */
    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest request) {
        try {
            LoginResponse response = authService.login(request);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }
}
