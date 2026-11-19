package com.hms.controller.platform;

import com.hms.service.platform.PlatformSubscriptionService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** Super Admin: demos, plan limits per hospital, subscription extension, and the overview. */
@RestController
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class PlatformSubscriptionController {

    private final PlatformSubscriptionService service;

    public PlatformSubscriptionController(PlatformSubscriptionService service) {
        this.service = service;
    }

    /** Demos, renewals, near-limit hospitals and the counts for the overview cards. */
    @GetMapping("/platform/subscriptions/overview")
    public ResponseEntity<Map<String, Object>> overview() {
        return ResponseEntity.ok(service.overview());
    }

    /** Body: {"action": "START" | "EXTEND" | "END" | "CONVERT", "days": 14}. */
    @PostMapping("/platform/hospitals/{id}/demo")
    public ResponseEntity<Map<String, Object>> demo(@PathVariable String id, @RequestBody Map<String, Object> body) {
        return ResponseEntity.ok(service.demoAction(id, str(body.get("action")), wholeNumber(body.get("days"))));
    }

    /** Body: {"days": 30}. */
    @PostMapping("/platform/hospitals/{id}/subscription/extend")
    public ResponseEntity<Map<String, Object>> extend(@PathVariable String id, @RequestBody Map<String, Object> body) {
        return ResponseEntity.ok(service.extendSubscription(id, wholeNumber(body.get("days"))));
    }

    @GetMapping("/platform/hospitals/{id}/limits")
    public ResponseEntity<Map<String, Object>> limits(@PathVariable String id) {
        return ResponseEntity.ok(service.limits(id));
    }

    /** Body: {"overrides": {"BEDS": 15, "DOCTORS": null}} — null / missing = use the plan's limit. */
    @PutMapping("/platform/hospitals/{id}/limits")
    public ResponseEntity<Map<String, Object>> setLimits(@PathVariable String id, @RequestBody Map<String, Object> body) {
        Map<String, Integer> overrides = new java.util.HashMap<>();
        if (body.get("overrides") instanceof Map<?, ?> m) {
            m.forEach((k, v) -> overrides.put(String.valueOf(k), wholeNumber(v)));
        }
        return ResponseEntity.ok(service.setLimitOverrides(id, overrides));
    }

    private static String str(Object o) {
        return o == null ? null : String.valueOf(o);
    }

    /** A whole number from JSON (number or numeric string), or null when blank. */
    private static Integer wholeNumber(Object o) {
        if (o == null || String.valueOf(o).isBlank()) return null;
        try {
            return (int) Math.round(Double.parseDouble(String.valueOf(o).trim()));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Enter a whole number");
        }
    }
}
