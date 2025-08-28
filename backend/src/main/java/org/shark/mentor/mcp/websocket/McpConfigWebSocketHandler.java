package org.shark.mentor.mcp.websocket;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.*;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.CopyOnWriteArraySet;

@Component
@Slf4j
public class McpConfigWebSocketHandler implements WebSocketHandler {

    private final CopyOnWriteArraySet<WebSocketSession> sessions = new CopyOnWriteArraySet<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        sessions.add(session);
        log.info("WebSocket connection established for MCP config updates: {}", session.getId());
    }

    @Override
    public void handleMessage(WebSocketSession session, WebSocketMessage<?> message) throws Exception {
        // This handler only sends messages, doesn't process incoming messages
        log.debug("Received message from WebSocket session {}: {}", session.getId(), message.getPayload());
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        log.error("WebSocket transport error for session {}: {}", session.getId(), exception.getMessage());
        sessions.remove(session);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus closeStatus) throws Exception {
        sessions.remove(session);
        log.info("WebSocket connection closed for session {}: {}", session.getId(), closeStatus);
    }

    @Override
    public boolean supportsPartialMessages() {
        return false;
    }

    /**
     * Notify all connected clients that MCP configuration has been reloaded
     */
    public void notifyConfigReload() {
        String message = "{\"type\":\"config-reload\",\"timestamp\":" + System.currentTimeMillis() + "}";
        
        sessions.removeIf(session -> {
            try {
                if (session.isOpen()) {
                    session.sendMessage(new TextMessage(message));
                    return false;
                } else {
                    return true; // Remove closed sessions
                }
            } catch (Exception e) {
                log.error("Failed to send config reload notification to session {}: {}", session.getId(), e.getMessage());
                return true; // Remove session on error
            }
        });
        
        log.info("Sent config reload notification to {} connected clients", sessions.size());
    }
}