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
    private final IntelligentToolSelector intelligentToolSelector;

    /**
     * Executes an MCP tool based on the user's message
     */
    public String executeTool(McpServer server, String userMessage) {
        try {
            // Detectar si el mensaje es para enable_toolset (puede ajustarse según UI/lógica real)
            if (userMessage != null && userMessage.trim().toLowerCase().startsWith("enable toolset")) {
                // Extraer el nombre del toolset del mensaje, ejemplo: "enable toolset github"
                String[] parts = userMessage.trim().split("\\s+");
                String toolsetName = parts.length > 2 ? parts[2] : null;
                if (toolsetName == null) {
                    return "Error: Debe especificar el nombre del toolset a habilitar.";
                }
                Map<String, Object> params = new HashMap<>();
                params.put("toolset", toolsetName);
                // Llamada genérica al método enable_toolset
                return mcpToolService.callMcpMethodViaHttp(server, "enable_toolset", params);
            }
            // Obtiene las tools usando el servicio centralizado
            List<Map<String, Object>> availableTools = mcpToolService.getTools(server);

            if (availableTools.isEmpty()) {
                log.warn("No tools available for server: {}", server.getName());
                return "There are no tools available on the selected MCP server.";
            }

            // Select the best tool using intelligent selector
            String toolName = intelligentToolSelector.selectBestTool(userMessage, availableTools, server);
            if (toolName == null) {
                log.warn("No suitable tool found for message: '{}'", userMessage);
                return "Unable to determine the appropriate tool for your request. Please be more specific about what you want to do.";
            }
            
            Map<String, Object> toolSchema = availableTools.stream()
                    .filter(t -> toolName.equals(t.get("name")))
                    .findFirst()
                    .orElse(availableTools.get(0));
            
            // Extract arguments using intelligent selector  
            Map<String, Object> arguments = intelligentToolSelector.extractToolArguments(
                    userMessage, toolName, toolSchema, server);

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
            return mcpToolService.callToolViaStdio(server, stdin, stdout, toolName, arguments);
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