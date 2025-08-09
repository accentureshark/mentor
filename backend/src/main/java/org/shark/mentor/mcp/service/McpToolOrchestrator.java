package org.shark.mentor.mcp.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.shark.mentor.mcp.model.McpServer;
import org.springframework.stereotype.Service;

import java.io.*;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;

/**
 * Simplified MCP tool orchestrator using langchain4j principles
 * while maintaining MCP protocol compliance
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class McpToolOrchestrator {

    private final McpServerService mcpServerService;
    private final McpToolService mcpToolService;

    /**
     * Executes an MCP tool based on the user's message
     */
    public String executeTool(McpServer server, String userMessage) {
        try {
            // Obtiene las tools usando el servicio centralizado
            List<Map<String, Object>> availableTools = mcpToolService.getTools(server);

            if (availableTools.isEmpty()) {
                log.warn("No tools available for server: {}", server.getName());
                return "There are no tools available on the selected MCP server.";
            }

            // Select the best tool (you can use the logic from McpToolService or here)
            String toolName = mcpToolService.selectBestTool(userMessage, server);
            if (toolName == null) {
                return "Unable to determine the appropriate tool for your request.";
            }
            Map<String, Object> toolSchema = availableTools.stream()
                    .filter(t -> toolName.equals(t.get("name")))
                    .findFirst()
                    .orElse(availableTools.get(0));
            Map<String, Object> arguments = mcpToolService.extractToolArguments(userMessage, toolName);

            log.info("Selected tool '{}' for message: {}", toolName, userMessage);

            // Ejecuta la tool seleccionada
            return executeSelectedTool(server, toolName, arguments);

        } catch (Exception e) {
            log.error("Error executing MCP tool for server {}: {}", server.getName(), e.getMessage(), e);
            return "Error executing the tool: " + e.getMessage();
        }
    }

    private String executeSelectedTool(McpServer server, String toolName, Map<String, Object> arguments) throws Exception {
        String protocol = extractProtocol(server.getUrl());

        if ("stdio".equalsIgnoreCase(protocol)) {
            OutputStream stdin = mcpServerService.getStdioInput(server.getId());
            InputStream stdout = mcpServerService.getStdioOutput(server.getId());
            if (stdin == null || stdout == null) {
                throw new IllegalStateException("STDIO streams not available for server: " + server.getId());
            }
            return mcpToolService.callToolViaStdio(stdin, stdout, toolName, arguments);
        } else {
            return mcpToolService.callToolViaHttp(server, toolName, arguments);
        }
    }

    private String extractProtocol(String url) {
        if (url == null || !url.contains("://")) {
            return "unknown";
        }
        return url.substring(0, url.indexOf("://"));
    }
}