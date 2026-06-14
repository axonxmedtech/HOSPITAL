package com.hms.controller.platform;

import com.hms.entity.Faq;
import com.hms.repository.FaqRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/platform/faqs")
@CrossOrigin(origins = { "http://localhost:3000", "http://localhost:5173" })
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class PlatformFaqController {

    @Autowired
    private FaqRepository faqRepository;

    @PostMapping
    public ResponseEntity<?> addFaq(@RequestBody Faq faq) {
        try {
            if (faq.getQuestion() == null || faq.getQuestion().trim().isEmpty() ||
                faq.getAnswer() == null || faq.getAnswer().trim().isEmpty()) {
                return ResponseEntity.badRequest().body("Question and answer are required.");
            }
            Faq saved = faqRepository.save(faq);
            return ResponseEntity.ok(saved);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteFaq(@PathVariable Long id) {
        try {
            if (!faqRepository.existsById(id)) {
                return ResponseEntity.notFound().build();
            }
            faqRepository.deleteById(id);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }
}
