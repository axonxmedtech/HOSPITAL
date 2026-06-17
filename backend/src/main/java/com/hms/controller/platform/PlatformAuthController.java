package com.hms.controller.platform;

import com.hms.dto.LoginRequest;
import com.hms.dto.LoginResponse;
import com.hms.service.platform.PlatformAuthService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/platform")
@CrossOrigin(origins = { "http://localhost:3000", "http://localhost:5173" })
public class PlatformAuthController {

    @Autowired
    private PlatformAuthService authService;

    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest request) {
        LoginResponse response = authService.login(request);
        return ResponseEntity.ok(response);
    }
}
