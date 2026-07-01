package com.hms.service.hospital;

import com.hms.security.HospitalWebSocketHandler;
import com.hms.service.whatsapp.WhatsAppService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * NotificationService - Facade pattern encapsulating the multi-tenant real-time
 * WebSocket alerts and WhatsApp messaging integrations.
 *
 * @author HMS Team
 * @version Phase-0.8
 */
@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    private HospitalWebSocketHandler webSocketHandler;

    @Autowired
    private WhatsAppService whatsAppService;

    /**
     * Broadcasts a real-time data refresh event to all active sessions of a hospital tenant.
     * Evicts stats cache on dashboard.
     *
     * @param hospitalId the target hospital tenant
     * @param eventType the event discriminator string
     * @param entityId the primary key of the modified database record
     */
    public void sendWebSocketRefresh(Long hospitalId, String eventType, Long entityId) {
        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("type", "REFRESH_DATA");
            payload.put("event", eventType);
            payload.put("entityId", entityId);
            payload.put("timestamp", java.time.Instant.now().toString());

            String json = objectMapper.writeValueAsString(payload);
            webSocketHandler.broadcast(hospitalId, json);
            log.info("Sent WebSocket refresh payload to hospitalId: {}. Event: {}", hospitalId, eventType);
        } catch (Exception e) {
            log.error("Failed to compile or broadcast WebSocket refresh payload", e);
        }
    }

    /**
     * Sends a WhatsApp notification to a patient or contact using WhatsAppService templates.
     *
     * @param hospitalId the hospital tenant
     * @param patientId the patient ID reference
     * @param phone the recipient phone number
     * @param templateName the Meta WhatsApp registered template name
     * @param msgType the internal category identifier for message logs
     * @param params parameter values for message template placeholders
     * @param mediaUrl optional URL for headers or document link attachment
     */
    public void sendWhatsAppNotification(Long hospitalId, Long patientId, String phone,
                                         String templateName, String msgType,
                                         List<String> params, String mediaUrl) {
        try {
            whatsAppService.sendWhatsApp(hospitalId, patientId, phone, templateName, msgType, params, mediaUrl);
            log.info("Dispatched WhatsApp template {} request for hospitalId: {}, patientId: {}", templateName, hospitalId, patientId);
        } catch (Exception e) {
            log.error("Failed to request WhatsApp message dispatch through service", e);
        }
    }
}
