package org.shark.mentor.mcp.service;

import lombok.extern.slf4j.Slf4j;
import org.shark.mentor.mcp.model.McpLogEntry;
import org.shark.mentor.mcp.model.McpServer;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Slf4j
public class McpLoggingService {
    private final HttpClient httpClient;
    private final Map<String, List<McpLogEntry>> serverLogs = new ConcurrentHashMap<>();

    public McpLoggingService() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    public void logMessage(String serverId, String level, String message, String data) {
        McpLogEntry entry = McpLogEntry.builder()
                .serverId(serverId)
                .level(level)
                .message(message)
                .data(data)
                .timestamp(System.currentTimeMillis())
                .build();
        
        serverLogs.computeIfAbsent(serverId, k -> new ArrayList<>()).add(entry);
        
        log.info("[MCP-{}] {}: {}", serverId, level, message);
        
        // Send log to server if it supports logging
        sendLogToServer(serverId, entry);
    }

    public List<McpLogEntry> getLogs(String serverId) {
        return serverLogs.getOrDefault(serverId, Collections.emptyList());
    }

    public List<McpLogEntry> getAllLogs() {
        List<McpLogEntry> allLogs = new ArrayList<>();
        for (List<McpLogEntry> logs : serverLogs.values()) {
            allLogs.addAll(logs);
        }
        allLogs.sort(Comparator.comparing(McpLogEntry::getTimestamp));
        return allLogs;
    }

    public void clearLogs(String serverId) {
        serverLogs.remove(serverId);
        log.info("Cleared logs for server: {}", serverId);
    }

    public void clearAllLogs() {
        serverLogs.clear();
        log.info("Cleared all MCP logs");
    }

    private void sendLogToServer(String serverId, McpLogEntry entry) {
        // This would send the log entry to the MCP server if it supports logging
        // For now, we just store it locally
    }

    public void logInfo(String serverId, String message) {
        logMessage(serverId, "info", message, null);
    }

    public void logWarning(String serverId, String message) {
        logMessage(serverId, "warning", message, null);
    }

    public void logError(String serverId, String message) {
        logMessage(serverId, "error", message, null);
    }

    public void logDebug(String serverId, String message) {
        logMessage(serverId, "debug", message, null);
    }
}