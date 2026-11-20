package com.hms.controller.hospital;

import com.hms.service.hospital.SidebarSettingsService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/** Settings → Sidebar. Display preferences only; permissions are enforced by each screen's API. */
@RestController
@RequestMapping({"/hospital/settings/sidebar", "/clinic/settings/sidebar"})
public class SidebarSettingsController {

    private final SidebarSettingsService service;

    public SidebarSettingsController(SidebarSettingsService service) {
        this.service = service;
    }

    /** The tabs hidden for the signed-in user's role. */
    @GetMapping("/mine")
    @PreAuthorize("hasAnyRole('HOSPITAL_ADMIN','DOCTOR','RECEPTIONIST','NURSE','NURSE_INCHARGE','PHARMACIST','OT_INCHARGE')")
    public ResponseEntity<Map<String, List<String>>> mine() {
        return ResponseEntity.ok(Map.of("hidden", service.hiddenForCurrentUser()));
    }

    @GetMapping
    @PreAuthorize("hasRole('HOSPITAL_ADMIN')")
    public ResponseEntity<Map<String, Object>> all() {
        return ResponseEntity.ok(service.all());
    }

    @PutMapping("/{role}")
    @PreAuthorize("hasRole('HOSPITAL_ADMIN')")
    public ResponseEntity<Map<String, List<String>>> update(@PathVariable String role,
            @RequestBody Map<String, List<String>> body) {
        return ResponseEntity.ok(Map.of("hidden", service.update(role, body == null ? null : body.get("hidden"))));
    }

    @DeleteMapping("/{role}")
    @PreAuthorize("hasRole('HOSPITAL_ADMIN')")
    public ResponseEntity<Map<String, List<String>>> reset(@PathVariable String role) {
        return ResponseEntity.ok(Map.of("hidden", service.reset(role)));
    }
}
