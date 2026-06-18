package com.hms.controller.platform;

import com.hms.entity.Faq;
import com.hms.repository.FaqRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/platform/faqs")
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class PlatformFaqController {

    @Autowired
    private FaqRepository faqRepository;

    @PostMapping
    public ResponseEntity<?> addFaq(@RequestBody Faq faq) {
        if (faq.getQuestion() == null || faq.getQuestion().trim().isEmpty() ||
            faq.getAnswer() == null || faq.getAnswer().trim().isEmpty()) {
            return ResponseEntity.badRequest().body("Question and answer are required.");
        }
        Faq saved = faqRepository.save(faq);
        return ResponseEntity.ok(saved);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteFaq(@PathVariable Long id) {
        if (!faqRepository.existsById(id)) {
            return ResponseEntity.notFound().build();
        }
        faqRepository.deleteById(id);
        return ResponseEntity.ok().build();
    }
}
