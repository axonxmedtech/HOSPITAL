package com.hms.controller.publicapi;

import com.hms.dto.PatientFeedbackSubmitRequest;
import com.hms.service.hospital.FeedbackService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Public, token-authenticated feedback submission (Form 03). No JWT required — the token
 * itself resolves patient/hospital server-side (BR-7). Requires
 * SecurityConfig to permitAll "/api/public/feedback/**".
 */
@RestController
@RequestMapping("/api/public/feedback")
public class PublicFeedbackController {

    @Autowired
    private FeedbackService feedbackService;

    @PostMapping("/{token}")
    public ResponseEntity<?> submit(@PathVariable String token, @RequestBody PatientFeedbackSubmitRequest request) {
        try {
            feedbackService.submitFeedback(token, request);
            return ResponseEntity.ok(java.util.Map.of("message", "Thank you for your feedback."));
        } catch (IllegalStateException e) {
            // BR-3: expired/used token -> 410 Gone
            return ResponseEntity.status(410).body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }
}
