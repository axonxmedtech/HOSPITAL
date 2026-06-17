package com.hms.controller.hospital;

import com.hms.dto.LoginRequest;
import com.hms.dto.LoginResponse;
import com.hms.service.hospital.HospitalAuthService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@CrossOrigin(origins = { "http://localhost:3000", "http://localhost:5173" })
public class HospitalAuthController {

    @Autowired
    private HospitalAuthService authService;

    @Autowired
    private com.hms.security.HospitalWebSocketHandler webSocketHandler;

    @Autowired
    private com.hms.repository.UserRepository userRepository;

    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest request) {
        LoginResponse response = authService.login(request);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/auth/me")
    public ResponseEntity<?> getProfile(java.security.Principal principal) {
        if (principal == null) {
            return ResponseEntity.status(401).body("Unauthorized");
        }
        LoginResponse response = authService.getProfile(principal.getName());
        return ResponseEntity.ok(response);
    }

    @PutMapping("/auth/profile")
    public ResponseEntity<?> updateProfile(java.security.Principal principal, @RequestBody com.hms.dto.ProfileUpdateRequest request) {
        if (principal == null) {
            return ResponseEntity.status(401).body("Unauthorized");
        }
        LoginResponse response = authService.updateProfile(principal.getName(), request);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/hospital/settings/fees")
    public ResponseEntity<?> getHospitalFees(java.security.Principal principal) {
        if (principal == null) return ResponseEntity.status(401).body("Unauthorized");
        com.hms.dto.HospitalFeesDTO dto = authService.getHospitalFees(principal.getName());
        return ResponseEntity.ok(dto);
    }

    @PutMapping("/hospital/settings/fees")
    public ResponseEntity<?> updateHospitalFees(java.security.Principal principal, @RequestBody com.hms.dto.HospitalFeesDTO fees) {
        if (principal == null) return ResponseEntity.status(401).body("Unauthorized");
        com.hms.dto.HospitalFeesDTO updated = authService.updateHospitalFees(principal.getName(), fees);
        userRepository.findByEmail(principal.getName()).ifPresent(user -> {
            webSocketHandler.broadcast(user.getHospitalId(), "{\"type\":\"SETTINGS_UPDATED\"}");
        });
        return ResponseEntity.ok(updated);
    }

    @GetMapping("/hospital/settings/operations")
    public ResponseEntity<?> getOperationsSettings(java.security.Principal principal) {
        if (principal == null) return ResponseEntity.status(401).body("Unauthorized");
        return ResponseEntity.ok(authService.getHospitalOperationsSettings(principal.getName()));
    }

    @PutMapping("/hospital/settings/operations")
    public ResponseEntity<?> updateOperationsSettings(java.security.Principal principal, @RequestBody com.hms.dto.HospitalSettingDTO dto) {
        if (principal == null) return ResponseEntity.status(401).body("Unauthorized");
        com.hms.dto.HospitalSettingDTO updated = authService.updateHospitalOperationsSettings(principal.getName(), dto);
        userRepository.findByEmail(principal.getName()).ifPresent(user -> {
            webSocketHandler.broadcast(user.getHospitalId(), "{\"type\":\"SETTINGS_UPDATED\"}");
        });
        return ResponseEntity.ok(updated);
    }
}
