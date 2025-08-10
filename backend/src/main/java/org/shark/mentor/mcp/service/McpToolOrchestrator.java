package org.shark.mentor.mcp.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.shark.mentor.mcp.model.McpServer;
import org.springframework.stereotype.Service;

import java.io.*;
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

            Map<String, Object> toolCall = prepareToolCall(toolSchema, arguments);
            return scheduleToolCall(server, toolCall);

        } catch (Exception e) {
            log.error("Error executing MCP tool for server {}: {}", server.getName(), e.getMessage(), e);
            return "Error executing the tool: " + e.getMessage();
        }
    }

    public String scheduleToolCall(McpServer server, JsonNode toolCall) throws Exception {
        Map<String, Object> callMap = objectMapper.convertValue(toolCall, Map.class);
        return scheduleToolCall(server, callMap);
    }

    public String scheduleToolCall(McpServer server, Map<String, Object> toolCall) throws Exception {
        String protocol = extractProtocol(server.getUrl());

        if ("stdio".equalsIgnoreCase(protocol)) {
            OutputStream stdin = mcpServerService.getStdioInput(server.getId());
            InputStream stdout = mcpServerService.getStdioOutput(server.getId());
            if (stdin == null || stdout == null) {
                throw new IllegalStateException("STDIO streams not available for server: " + server.getId());
            }
            return mcpToolService.callToolViaStdio(stdin, stdout, toolCall);
        } else {
            return mcpToolService.callToolViaHttp(server, toolCall);
        }
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