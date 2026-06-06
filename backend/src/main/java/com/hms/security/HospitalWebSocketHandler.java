package com.hms.security;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * HospitalWebSocketHandler - Live real-time sync handler for multi-tenant hospital sessions.
 */
@Component
public class HospitalWebSocketHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(HospitalWebSocketHandler.class);

    // Concurrent map of hospitalId -> list of active WebSocket sessions
    private final Map<Long, List<WebSocketSession>> sessions = new ConcurrentHashMap<>();

    @org.springframework.beans.factory.annotation.Autowired
    @org.springframework.context.annotation.Lazy
    private com.hms.service.hospital.HospitalStatsService statsService;

    private static final int MAX_SESSIONS_PER_HOSPITAL = 50; // Guard against resource/session exhaustion

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        Long hospitalId = getHospitalId(session);
        if (hospitalId != null) {
            List<WebSocketSession> hospitalSessions = sessions.computeIfAbsent(hospitalId, k -> new CopyOnWriteArrayList<>());
            
            // Limit sessions per hospital tenant to prevent resource/memory exhaustion
            if (hospitalSessions.size() >= MAX_SESSIONS_PER_HOSPITAL) {
                log.warn("WebSocket session limit reached ({}) for hospitalId: {}. Closing session: {}", 
                         MAX_SESSIONS_PER_HOSPITAL, hospitalId, session.getId());
                session.close(CloseStatus.POLICY_VIOLATION);
                return;
            }
            
            hospitalSessions.add(session);
            log.info("WebSocket connection established for hospitalId: {}, session ID: {}", hospitalId, session.getId());
        } else {
            log.warn("WebSocket connection attempted with invalid hospitalId path. Session ID: {}", session.getId());
            session.close(CloseStatus.BAD_DATA);
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        Long hospitalId = getHospitalId(session);
        if (hospitalId != null) {
            List<WebSocketSession> hospitalSessions = sessions.get(hospitalId);
            if (hospitalSessions != null) {
                hospitalSessions.remove(session);
                if (hospitalSessions.isEmpty()) {
                    sessions.remove(hospitalId);
                }
            }
            log.info("WebSocket connection closed for hospitalId: {}, session ID: {}, status: {}", hospitalId, session.getId(), status);
        }
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        log.error("WebSocket transport error for session: {}", session.getId(), exception);
        try {
            session.close(CloseStatus.SERVER_ERROR);
        } catch (IOException e) {
            log.error("Error closing session after transport error", e);
        }
    }

    /**
     * Broadcasts a JSON message to all active WebSocket sessions registered under the specified hospitalId.
     */
    public void broadcast(Long hospitalId, String jsonPayload) {
        if (hospitalId == null) return;

        // Evict cached dashboard stats when any data changes
        if (jsonPayload != null && jsonPayload.contains("REFRESH_DATA")) {
            try {
                if (statsService != null) {
                    statsService.evictStats(hospitalId);
                }
            } catch (Exception e) {
                log.error("Failed to evict stats cache for hospitalId: {}", hospitalId, e);
            }
        }

        List<WebSocketSession> hospitalSessions = sessions.get(hospitalId);
        if (hospitalSessions == null || hospitalSessions.isEmpty()) {
            return;
        }
        log.info("Broadcasting to hospitalId: {}, message: {}", hospitalId, jsonPayload);
        TextMessage message = new TextMessage(jsonPayload);
        
        java.util.concurrent.CompletableFuture.runAsync(() -> {
            for (WebSocketSession session : hospitalSessions) {
                if (session.isOpen()) {
                    try {
                        synchronized (session) {
                            session.sendMessage(message);
                        }
                    } catch (Exception e) {
                        log.error("Failed to send WebSocket message to session: {}", session.getId(), e);
                    }
                }
            }
        });
    }

    private Long getHospitalId(WebSocketSession session) {
        // Enforce using the attribute populated by our handshake interceptor
        Object idObj = session.getAttributes().get("hospitalId");
        if (idObj instanceof Long) {
            return (Long) idObj;
        }
        
        // Fallback/Legacy parsing if interceptor was bypassed or not registered
        try {
            String path = session.getUri().getPath();
            String[] parts = path.split("/");
            return Long.parseLong(parts[parts.length - 1]);
        } catch (Exception e) {
            log.error("Error parsing hospitalId from WebSocket path", e);
            return null;
        }
    }
}
