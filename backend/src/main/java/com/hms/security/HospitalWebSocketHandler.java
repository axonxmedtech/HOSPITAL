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

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        Long hospitalId = getHospitalId(session);
        if (hospitalId != null) {
            sessions.computeIfAbsent(hospitalId, k -> new CopyOnWriteArrayList<>()).add(session);
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
        List<WebSocketSession> hospitalSessions = sessions.get(hospitalId);
        if (hospitalSessions == null || hospitalSessions.isEmpty()) {
            return;
        }
        log.info("Broadcasting to hospitalId: {}, message: {}", hospitalId, jsonPayload);
        TextMessage message = new TextMessage(jsonPayload);
        for (WebSocketSession session : hospitalSessions) {
            if (session.isOpen()) {
                try {
                    session.sendMessage(message);
                } catch (IOException e) {
                    log.error("Failed to send WebSocket message to session: {}", session.getId(), e);
                }
            }
        }
    }

    private Long getHospitalId(WebSocketSession session) {
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
