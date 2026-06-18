package com.hms.controller.publicapi;

import com.hms.repository.FaqRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/public")
public class PublicFaqController {

    @Autowired
    private FaqRepository faqRepository;

    @GetMapping("/faqs")
    public ResponseEntity<?> getAllFaqs() {
        return ResponseEntity.ok(faqRepository.findAll());
    }
}
