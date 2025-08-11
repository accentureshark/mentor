package org.shark.mentor.mcp.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.shark.mentor.mcp.model.McpServer;
import org.springframework.stereotype.Service;

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
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Executes an MCP tool based on the user's message
     */
    public String executeTool(McpServer server, String userMessage) {
        try {
            // 1. Obtener tools disponibles del servidor MCP
            List<Map<String, Object>> availableTools = mcpToolService.getTools(server);
            if (availableTools.isEmpty()) {
                log.warn("No tools available for server: {}", server.getName());
                return "No hay herramientas disponibles en el servidor MCP seleccionado.";
            }

            // 2. Inferir la tool y argumentos a partir del mensaje y la metadata de tools
            // (Esto puede ser implementado usando LLM o heurística genérica, pero sin hardcode de dominio)
            Map<String, Object> inference = mcpToolService.inferToolAndArguments(userMessage, availableTools, server);
            if (inference == null || !inference.containsKey("tool") || !inference.containsKey("arguments")) {
                return "No se pudo determinar la herramienta o los argumentos apropiados para la consulta.";
            }
            String toolName = (String) inference.get("tool");
            Map<String, Object> arguments = (Map<String, Object>) inference.get("arguments");

            // 3. Buscar el schema de la tool seleccionada
            Map<String, Object> toolSchema = availableTools.stream()
                    .filter(t -> toolName.equals(t.get("name")))
                    .findFirst()
                    .orElse(null);
            if (toolSchema == null) {
                return "La herramienta seleccionada no está disponible en el servidor MCP.";
            }

            // 4. Preparar y ejecutar la llamada a la tool
            Map<String, Object> toolCall = prepareToolCall(toolSchema, arguments);
            String result = scheduleToolCall(server, toolCall);
            return result;
        } catch (Exception e) {
            log.error("Error ejecutando herramienta MCP para el servidor {}: {}", server.getName(), e.getMessage(), e);
            return "Error ejecutando la herramienta: " + e.getMessage();
        }
    }

    public String scheduleToolCall(McpServer server, JsonNode toolCall) throws Exception {
        Map<String, Object> callMap = objectMapper.convertValue(toolCall, Map.class);
        return scheduleToolCall(server, callMap);
    }

    public String scheduleToolCall(McpServer server, Map<String, Object> toolCall) throws Exception {
        String protocol = extractProtocol(server.getUrl());

        return mcpToolService.callTool(server, toolCall);
    }

    public Map<String, Object> prepareToolCall(Map<String, Object> toolSchema, Map<String, Object> arguments) {
        Map<String, Object> inputSchema = (Map<String, Object>) (toolSchema.get("inputSchema") != null ?
                toolSchema.get("inputSchema") : toolSchema.get("input_schema"));
        Map<String, Object> properties = inputSchema != null ? (Map<String, Object>) inputSchema.get("properties") : Collections.emptyMap();
        Map<String, Object> filteredArgs = new HashMap<>();
        for (String key : properties.keySet()) {
            if (arguments.containsKey(key)) {
                filteredArgs.put(key, arguments.get(key));
            }
        }
        Map<String, Object> params = new HashMap<>();
        params.put("name", toolSchema.get("name"));
        params.put("arguments", filteredArgs);
        Map<String, Object> call = new HashMap<>();
        call.put("jsonrpc", "2.0");
        call.put("id", UUID.randomUUID().toString());
        call.put("method", "tools/call");
        call.put("params", params);
        return call;
    }

    private String extractProtocol(String url) {
        if (url == null || !url.contains("://")) {
            return "unknown";
        }
        return url.substring(0, url.indexOf("://"));
    }
}