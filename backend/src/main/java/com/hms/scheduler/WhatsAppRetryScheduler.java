package com.hms.scheduler;

import com.hms.entity.WhatsAppMessageLog;
import com.hms.repository.WhatsAppMessageLogRepository;
import com.hms.service.whatsapp.WhatsAppService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Component
public class WhatsAppRetryScheduler {

    private static final Logger log = LoggerFactory.getLogger(WhatsAppRetryScheduler.class);
    private static final int MAX_RETRIES = 2;

    private final WhatsAppMessageLogRepository logRepository;
    private final WhatsAppService whatsAppService;

    public WhatsAppRetryScheduler(WhatsAppMessageLogRepository logRepository,
                                  WhatsAppService whatsAppService) {
        this.logRepository = logRepository;
        this.whatsAppService = whatsAppService;
    }

    @Scheduled(fixedDelay = 300000) // every 5 minutes
    public void retryFailed() {
        List<WhatsAppMessageLog> eligible = logRepository
                .findByStatusAndNextRetryAtBeforeAndRetryCountLessThan(
                        WhatsAppMessageLog.STATUS_FAILED, LocalDateTime.now(), MAX_RETRIES);

        if (!eligible.isEmpty()) {
            log.info("WhatsAppRetryScheduler: retrying {} failed messages", eligible.size());
        }

        for (WhatsAppMessageLog entry : eligible) {
            try {
                whatsAppService.retry(entry);
            } catch (Exception e) {
                log.warn("Retry processing failed for log entry {}: {}", entry.getId(), e.getMessage());
            }
        }
    }
}
