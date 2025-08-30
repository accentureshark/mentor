package org.shark.mentor.mcp.service;

import lombok.extern.slf4j.Slf4j;
import org.shark.mentor.mcp.model.McpNotification;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Slf4j
public class McpNotificationService {
    private final Map<String, List<McpNotification>> serverNotifications = new ConcurrentHashMap<>();

    public void handleNotification(String serverId, String method, Object params) {
        McpNotification notification = McpNotification.builder()
                .serverId(serverId)
                .method(method)
                .params(params)
                .timestamp(System.currentTimeMillis())
                .build();
        
        serverNotifications.computeIfAbsent(serverId, k -> new ArrayList<>()).add(notification);
        
        log.info("[MCP-NOTIFICATION] {}: {} - {}", serverId, method, params);
        
        // Handle specific notification types
        switch (method) {
            case "resources/updated":
                handleResourcesUpdated(serverId, params);
                break;
            case "tools/list_changed":
                handleToolsListChanged(serverId, params);
                break;
            case "prompts/list_changed":
                handlePromptsListChanged(serverId, params);
                break;
            default:
                log.debug("Unhandled notification method: {}", method);
        }
    }

    public List<McpNotification> getNotifications(String serverId) {
        return serverNotifications.getOrDefault(serverId, Collections.emptyList());
    }

    public List<McpNotification> getAllNotifications() {
        List<McpNotification> allNotifications = new ArrayList<>();
        for (List<McpNotification> notifications : serverNotifications.values()) {
            allNotifications.addAll(notifications);
        }
        allNotifications.sort(Comparator.comparing(McpNotification::getTimestamp));
        return allNotifications;
    }

    public void clearNotifications(String serverId) {
        serverNotifications.remove(serverId);
        log.info("Cleared notifications for server: {}", serverId);
    }

    public void clearAllNotifications() {
        serverNotifications.clear();
        log.info("Cleared all MCP notifications");
    }

    private void handleResourcesUpdated(String serverId, Object params) {
        log.info("Resources updated for server {}: {}", serverId, params);
        // Could trigger a refresh of resources cache here
    }

    private void handleToolsListChanged(String serverId, Object params) {
        log.info("Tools list changed for server {}: {}", serverId, params);
        // Could trigger a refresh of tools cache here
    }

    private void handlePromptsListChanged(String serverId, Object params) {
        log.info("Prompts list changed for server {}: {}", serverId, params);
        // Could trigger a refresh of prompts cache here
    }
}