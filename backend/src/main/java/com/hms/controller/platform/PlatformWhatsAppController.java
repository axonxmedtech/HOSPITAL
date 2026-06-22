package com.hms.controller.platform;

import com.hms.dto.WhatsAppStatsDTO;
import com.hms.entity.WhatsAppMessageLog;
import com.hms.repository.WhatsAppMessageLogRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

@RestController
@RequestMapping("/platform/whatsapp")
public class PlatformWhatsAppController {

    private final WhatsAppMessageLogRepository logRepository;

    public PlatformWhatsAppController(WhatsAppMessageLogRepository logRepository) {
        this.logRepository = logRepository;
    }

    @GetMapping("/stats")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<WhatsAppStatsDTO> getStats() {
        LocalDateTime startOfDay = LocalDateTime.now().toLocalDate().atStartOfDay();
        long failed = logRepository.countByStatusSince(
                WhatsAppMessageLog.STATUS_PERMANENTLY_FAILED, startOfDay);
        long hospitals = logRepository.countDistinctHospitalsByStatusSince(
                WhatsAppMessageLog.STATUS_PERMANENTLY_FAILED, startOfDay);
        return ResponseEntity.ok(new WhatsAppStatsDTO(failed, hospitals));
    }
}
