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
     * Ejecuta una tool MCP según el mensaje del usuario
     */
    public String executeTool(McpServer server, String userMessage) {
        try {
            // Obtiene las tools usando el servicio centralizado
            List<Map<String, Object>> availableTools = mcpToolService.getTools(server);

            if (availableTools.isEmpty()) {
                log.warn("No tools available for server: {}", server.getName());
                return "No hay herramientas disponibles en el servidor MCP seleccionado.";
            }

            // Selecciona la mejor tool (puedes usar la lógica de McpToolService o aquí)
            String toolName = mcpToolService.selectBestTool(userMessage, server);
            if (toolName == null) {
                return "No se pudo determinar la herramienta adecuada para tu solicitud.";
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
            log.error("Error ejecutando tool MCP para el servidor {}: {}", server.getName(), e.getMessage(), e);
            return "Error ejecutando la herramienta: " + e.getMessage();
        }
    }

    private String executeSelectedTool(McpServer server, String toolName, Map<String, Object> arguments) throws Exception {
        String protocol = extractProtocol(server.getUrl());

        if ("stdio".equalsIgnoreCase(protocol)) {
            OutputStream stdin = mcpServerService.getStdioInput(server.getId());
            InputStream stdout = mcpServerService.getStdioOutput(server.getId());
            if (stdin == null || stdout == null) {
                throw new IllegalStateException("STDIO streams no disponibles para el servidor: " + server.getId());
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