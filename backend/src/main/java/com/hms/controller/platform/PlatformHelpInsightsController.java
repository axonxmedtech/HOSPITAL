package com.hms.controller.platform;

import com.hms.service.help.HelpFeedbackService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** Super Admin: what help users could not find, and how each answer is rated. */
@RestController
@RequestMapping("/platform/help")
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class PlatformHelpInsightsController {

    private final HelpFeedbackService service;

    public PlatformHelpInsightsController(HelpFeedbackService service) {
        this.service = service;
    }

    @GetMapping("/insights")
    public ResponseEntity<Map<String, Object>> insights(@RequestParam(defaultValue = "30") int days) {
        return ResponseEntity.ok(service.insights(days));
    }

    /** An answer has been written for this question: take it off the list. */
    @PutMapping("/questions/resolve")
    public ResponseEntity<Map<String, Object>> resolve(@RequestBody Map<String, String> body) {
        return ResponseEntity.ok(Map.of("resolved", service.resolve(body.get("key"))));
    }
}
