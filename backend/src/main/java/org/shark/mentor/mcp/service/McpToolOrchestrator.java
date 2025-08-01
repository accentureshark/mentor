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
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    /**
     * Execute MCP tool call with simplified error handling and response processing
     */
    public String executeTool(McpServer server, String userMessage) {
        try {
            // Get available tools from the server
            List<Map<String, Object>> availableTools = getAvailableTools(server);
            
            if (availableTools.isEmpty()) {
                log.warn("No tools available for server: {}", server.getName());
                return "No tools available on the selected MCP server.";
            }

            // Select the most appropriate tool using simplified logic
            ToolSelection selection = selectBestTool(userMessage, availableTools);
            
            if (selection == null) {
                return "Could not determine appropriate tool for your request.";
            }

            log.info("Selected tool '{}' for message: {}", selection.getToolName(), userMessage);

            // Execute the selected tool
            return executeSelectedTool(server, selection);

        } catch (Exception e) {
            log.error("Error executing MCP tool for server {}: {}", server.getName(), e.getMessage(), e);
            return "Error executing tool: " + e.getMessage();
        }
    }

    /**
     * Get available tools from MCP server using JSON-RPC protocol
     */
    private List<Map<String, Object>> getAvailableTools(McpServer server) {
        String protocol = extractProtocol(server.getUrl());
        
        try {
            if ("stdio".equalsIgnoreCase(protocol)) {
                return getToolsViaStdio(server);
            } else if ("http".equalsIgnoreCase(protocol) || "https".equalsIgnoreCase(protocol)) {
                return getToolsViaHttp(server);
            } else {
                log.warn("Unsupported protocol for tools/list: {}", protocol);
                return Collections.emptyList();
            }
        } catch (Exception e) {
            log.error("Failed to get tools from server {}: {}", server.getName(), e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * Select the best tool based on user message content
     */
    private ToolSelection selectBestTool(String userMessage, List<Map<String, Object>> tools) {
        String messageLower = userMessage.toLowerCase();
        
        // Simple keyword-based tool selection
        for (Map<String, Object> tool : tools) {
            String toolName = (String) tool.get("name");
            String description = (String) tool.get("description");
            
            if (toolName == null) continue;
            
            // Check if tool name or description keywords match the user message
            if (matchesTool(messageLower, toolName, description)) {
                Map<String, Object> arguments = extractToolArguments(userMessage, toolName, tool);
                return new ToolSelection(toolName, arguments, tool);
            }
        }
        
        // Default to first available tool
        if (!tools.isEmpty()) {
            Map<String, Object> defaultTool = tools.get(0);
            String toolName = (String) defaultTool.get("name");
            Map<String, Object> arguments = extractToolArguments(userMessage, toolName, defaultTool);
            return new ToolSelection(toolName, arguments, defaultTool);
        }
        
        return null;
    }

    /**
     * Check if user message matches a tool
     */
    private boolean matchesTool(String messageLower, String toolName, String description) {
        // Check tool name
        if (toolName != null && messageLower.contains(toolName.toLowerCase())) {
            return true;
        }
        
        // Check description keywords
        if (description != null) {
            String[] keywords = description.toLowerCase().split("\\W+");
            for (String keyword : keywords) {
                if (keyword.length() > 3 && messageLower.contains(keyword)) {
                    return true;
                }
            }
        }
        
        // Common action words
        if (toolName != null) {
            String toolLower = toolName.toLowerCase();
            if ((toolLower.contains("search") && (messageLower.contains("search") || messageLower.contains("find"))) ||
                (toolLower.contains("list") && (messageLower.contains("list") || messageLower.contains("show"))) ||
                (toolLower.contains("get") && (messageLower.contains("get") || messageLower.contains("retrieve")))) {
                return true;
            }
        }
        
        return false;
    }

    /**
     * Extract tool arguments from user message
     */
    private Map<String, Object> extractToolArguments(String userMessage, String toolName, Map<String, Object> toolSchema) {
        Map<String, Object> arguments = new HashMap<>();
        
        // Extract arguments based on tool schema if available
        Object inputSchema = toolSchema.get("inputSchema");
        if (inputSchema instanceof Map) {
            Map<String, Object> schema = (Map<String, Object>) inputSchema;
            Object properties = schema.get("properties");
            
            if (properties instanceof Map) {
                Map<String, Object> props = (Map<String, Object>) properties;
                
                // Simple extraction logic for common parameters
                for (String propName : props.keySet()) {
                    String value = extractParameterValue(userMessage, propName);
                    if (value != null && !value.isEmpty()) {
                        arguments.put(propName, value);
                    }
                }
            }
        }
        
        // Fallback to common argument patterns
        if (arguments.isEmpty()) {
            arguments.putAll(extractCommonArguments(userMessage, toolName));
        }
        
        return arguments;
    }

    /**
     * Extract parameter value from user message
     */
    private String extractParameterValue(String message, String paramName) {
        // Simple parameter extraction logic
        String[] commonQueryParams = {"q", "query", "search", "term"};
        
        for (String queryParam : commonQueryParams) {
            if (paramName.equalsIgnoreCase(queryParam)) {
                // Extract search terms from message
                return extractSearchTerms(message);
            }
        }
        
        return null;
    }

    /**
     * Extract search terms from user message
     */
    private String extractSearchTerms(String message) {
        // Remove common stop words and extract meaningful terms
        String cleaned = message.replaceAll("(?i)\\b(search|find|look|for|get|show|list|me|the|a|an)\\b", "").trim();
        return cleaned.isEmpty() ? message : cleaned;
    }

    /**
     * Extract common arguments for known tool patterns
     */
    private Map<String, Object> extractCommonArguments(String message, String toolName) {
        Map<String, Object> args = new HashMap<>();
        
        // Common patterns for different tool types
        if (toolName != null) {
            String toolLower = toolName.toLowerCase();
            
            if (toolLower.contains("search")) {
                args.put("q", extractSearchTerms(message));
            } else if (toolLower.contains("list") || toolLower.contains("get")) {
                // For list/get operations, often no additional parameters needed
                // Could add pagination, filtering, etc. here
            }
        }
        
        return args;
    }

    /**
     * Execute the selected tool via appropriate protocol
     */
    private String executeSelectedTool(McpServer server, ToolSelection selection) throws Exception {
        String protocol = extractProtocol(server.getUrl());
        
        if ("stdio".equalsIgnoreCase(protocol)) {
            return executeToolViaStdio(server, selection);
        } else if ("http".equalsIgnoreCase(protocol) || "https".equalsIgnoreCase(protocol)) {
            return executeToolViaHttp(server, selection);
        } else {
            throw new IllegalArgumentException("Unsupported protocol: " + protocol);
        }
    }

    private String executeToolViaStdio(McpServer server, ToolSelection selection) throws Exception {
        OutputStream stdin = mcpServerService.getStdioInput(server.getId());
        InputStream stdout = mcpServerService.getStdioOutput(server.getId());
        
        if (stdin == null || stdout == null) {
            throw new IllegalStateException("STDIO streams not available for server: " + server.getId());
        }

        // Build JSON-RPC tool call
        Map<String, Object> toolCall = Map.of(
                "jsonrpc", "2.0",
                "id", UUID.randomUUID().toString(),
                "method", "tools/call",
                "params", Map.of(
                        "name", selection.getToolName(),
                        "arguments", selection.getArguments()
                )
        );

        String json = objectMapper.writeValueAsString(toolCall);
        log.debug("Sending STDIO tool call: {}", json);
        
        stdin.write((json + "\n").getBytes());
        stdin.flush();

        String response = new BufferedReader(new InputStreamReader(stdout)).readLine();
        return parseToolResponse(response);
    }

    private String executeToolViaHttp(McpServer server, ToolSelection selection) throws Exception {
        Map<String, Object> toolCall = Map.of(
                "jsonrpc", "2.0",
                "id", UUID.randomUUID().toString(),
                "method", "tools/call",
                "params", Map.of(
                        "name", selection.getToolName(),
                        "arguments", selection.getArguments()
                )
        );

        String json = objectMapper.writeValueAsString(toolCall);
        log.debug("Sending HTTP tool call: {}", json);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(server.getUrl() + "/mcp"))
                .timeout(Duration.ofSeconds(30))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        return parseToolResponse(response.body());
    }

    /**
     * Parse MCP tool response and extract meaningful content
     */
    private String parseToolResponse(String response) {
        if (response == null || response.trim().isEmpty()) {
            return "No response from MCP server";
        }

        try {
            JsonNode root = objectMapper.readTree(response);
            
            if (root.has("result")) {
                JsonNode result = root.get("result");
                return formatToolResult(result);
            } else if (root.has("error")) {
                JsonNode error = root.get("error");
                return "MCP Error: " + error.get("message").asText();
            } else {
                return "Unexpected MCP response format: " + response;
            }
        } catch (Exception e) {
            log.warn("Could not parse MCP response as JSON: {}", response);
            return response; // Return raw response if not valid JSON
        }
    }

    /**
     * Format tool result for user consumption
     */
    private String formatToolResult(JsonNode result) {
        if (result.isTextual()) {
            return result.asText();
        } else if (result.isObject() || result.isArray()) {
            try {
                return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(result);
            } catch (Exception e) {
                return result.toString();
            }
        } else {
            return result.toString();
        }
    }

    private String extractProtocol(String url) {
        if (url == null || !url.contains("://")) {
            return "unknown";
        }
        return url.substring(0, url.indexOf("://"));
    }

    private List<Map<String, Object>> getToolsViaStdio(McpServer server) throws Exception {
        // Simplified STDIO tools/list implementation
        return Collections.emptyList(); // TODO: Implement when needed
    }

    private List<Map<String, Object>> getToolsViaHttp(McpServer server) throws Exception {
        Map<String, Object> request = Map.of(
                "jsonrpc", "2.0",
                "id", UUID.randomUUID().toString(),
                "method", "tools/list",
                "params", Collections.emptyMap()
        );

        String json = objectMapper.writeValueAsString(request);
        
        HttpRequest httpRequest = HttpRequest.newBuilder()
                .uri(URI.create(server.getUrl() + "/mcp"))
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();

        HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
        
        if (response.statusCode() >= 200 && response.statusCode() < 300) {
            JsonNode root = objectMapper.readTree(response.body());
            if (root.has("result") && root.get("result").has("tools")) {
                JsonNode tools = root.get("result").get("tools");
                return objectMapper.convertValue(tools, List.class);
            }
        }
        
        return Collections.emptyList();
    }

    /**
     * Tool selection result
     */
    private static class ToolSelection {
        private final String toolName;
        private final Map<String, Object> arguments;
        private final Map<String, Object> toolSchema;

        public ToolSelection(String toolName, Map<String, Object> arguments, Map<String, Object> toolSchema) {
            this.toolName = toolName;
            this.arguments = arguments;
            this.toolSchema = toolSchema;
        }

        public String getToolName() { return toolName; }
        public Map<String, Object> getArguments() { return arguments; }
        public Map<String, Object> getToolSchema() { return toolSchema; }
    }
}