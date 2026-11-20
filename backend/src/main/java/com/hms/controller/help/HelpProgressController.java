package com.hms.controller.help;

import com.fasterxml.jackson.databind.JsonNode;
import com.hms.service.help.HelpFeedbackService;
import com.hms.service.help.HelpProgressService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * In-app help for every signed-in role, including the Super Admin: the caller's own progress
 * (guide steps ticked, tours seen; always their own row, no id in the path) and feedback on answers.
 */
@RestController
public class HelpProgressController {

    private final HelpProgressService service;
    private final HelpFeedbackService feedback;

    public HelpProgressController(HelpProgressService service, HelpFeedbackService feedback) {
        this.service = service;
        this.feedback = feedback;
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

    /** "Was this helpful?" and questions the assistant could not answer. */
    @PostMapping("/help/feedback")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Map<String, Object>> feedback(@RequestBody Map<String, String> body) {
        boolean saved = feedback.record(body.get("kind"), body.get("entryId"), body.get("question"),
                body.get("screen"));
        return ResponseEntity.ok(Map.of("saved", saved));
    }
}
