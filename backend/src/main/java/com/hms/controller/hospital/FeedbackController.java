package com.hms.controller.hospital;

import com.hms.dto.FeedbackTokenIssueRequest;
import com.hms.service.hospital.FeedbackService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * Patient Feedback administration (Form 03). BR-6: doctors/nurses/reception have zero
 * read access — restricted to Hospital Admin (acting as the quality/admin capacity).
 */
@RestController
@RequestMapping("/hospital/feedback")
public class FeedbackController {

    @Autowired
    private FeedbackService feedbackService;

    @PostMapping("/tokens")
    @PreAuthorize("hasAnyRole('HOSPITAL_ADMIN', 'RECEPTIONIST')")
    public ResponseEntity<?> issueToken(@RequestBody FeedbackTokenIssueRequest request) {
        try {
            return ResponseEntity.ok(feedbackService.issueToken(request));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @GetMapping
    @PreAuthorize("hasRole('HOSPITAL_ADMIN')")
    public ResponseEntity<?> getFeedback() {
        return ResponseEntity.ok(feedbackService.getFeedback());
    }

    @GetMapping("/complaints")
    @PreAuthorize("hasRole('HOSPITAL_ADMIN')")
    public ResponseEntity<?> getComplaints() {
        return ResponseEntity.ok(feedbackService.getComplaints());
    }

    @PutMapping("/complaints/{id}/resolve")
    @PreAuthorize("hasRole('HOSPITAL_ADMIN')")
    public ResponseEntity<?> resolveComplaint(@PathVariable Long id, @RequestBody java.util.Map<String, String> body) {
        try {
            return ResponseEntity.ok(feedbackService.resolveComplaint(id, body.get("resolution")));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }
}
