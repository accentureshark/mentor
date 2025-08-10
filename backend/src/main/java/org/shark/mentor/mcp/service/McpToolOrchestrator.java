package org.shark.mentor.mcp.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.shark.mentor.mcp.model.McpServer;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;
import java.util.Map;
import java.util.Optional;

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
            List<Map<String, Object>> availableTools = mcpToolService.getTools(server);

            if (availableTools.isEmpty()) {
                log.warn("No tools available for server: {}", server.getName());
                return "There are no tools available on the selected MCP server.";
            }

            Map<String, Object> toolCall = mcpToolService.scheduleToolCall(userMessage, availableTools);
            if (toolCall == null) {
                return "Unable to determine the appropriate tool for your request.";
            }

            String toolName = Optional.ofNullable(toolCall.get("params"))
                    .filter(Map.class::isInstance)
                    .map(Map.class::cast)
                    .map(p -> (String) p.get("name"))
                    .orElse("unknown");

            log.info("Selected tool '{}' for message: {}", toolName, userMessage);

            return executeSelectedTool(server, toolCall);
        } catch (Exception e) {
            log.error("Error executing MCP tool for server {}: {}", server.getName(), e.getMessage(), e);
            return "Error executing the tool: " + e.getMessage();
        }
    }

    private String executeSelectedTool(McpServer server, Map<String, Object> toolCall) throws Exception {
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

    private String extractProtocol(String url) {
        if (url == null || !url.contains("://")) {
            return "unknown";
        }
        return url.substring(0, url.indexOf("://"));
    }
}