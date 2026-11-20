package com.hms.controller.help;

import com.fasterxml.jackson.databind.JsonNode;
import com.hms.service.help.HelpProgressService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * The signed-in user's own in-app help progress (guide steps ticked, tours seen), for every role
 * including the Super Admin. Always the caller's own row; there is no id in the path.
 */
@RestController
public class HelpProgressController {

    private final HelpProgressService service;

    public HelpProgressController(HelpProgressService service) {
        this.service = service;
    }

    @GetMapping("/help/progress")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<JsonNode> read() {
        return ResponseEntity.ok(service.read());
    }

    @PutMapping("/help/progress")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<JsonNode> save(@RequestBody JsonNode data) {
        return ResponseEntity.ok(service.save(data));
    }
}
