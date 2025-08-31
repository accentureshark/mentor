package org.shark.mentor.mcp.application.service.tool;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.shark.mentor.mcp.application.service.server.McpServerService;
import org.shark.mentor.mcp.domain.model.McpServer;
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
    private final IntelligentToolSelector intelligentToolSelector;
    private final DynamicToolsetManager dynamicToolsetManager;

    /**
     * Executes an MCP tool based on the user's message with intelligent toolset discovery
     */
    public String executeTool(McpServer server, String userMessage) {
        try {
            log.info("Processing tool request: '{}' for server: {}", userMessage, server.getName());
            
            // Check for direct toolset management requests
            if (isDirectToolsetRequest(userMessage)) {
                return handleDirectToolsetRequest(server, userMessage);
            }
            
            // Get available tools
            List<Map<String, Object>> availableTools = mcpToolService.getTools(server);
            log.debug("Found {} available tools initially", availableTools.size());

            // Check if we have the capability to handle the user's request
            if (!dynamicToolsetManager.hasCapabilityForIntent(availableTools, userMessage)) {
                log.info("Required capability not found, attempting to enable relevant toolsets");
                
                // Try to enable toolsets that might help
                List<String> missingCapabilities = identifyMissingCapabilities(userMessage, availableTools);
                boolean toolsetsEnabled = dynamicToolsetManager.enableToolsetsForIntent(server, userMessage, missingCapabilities);
                
                if (toolsetsEnabled) {
                    // Refresh available tools after enabling toolsets
                    availableTools = mcpToolService.getTools(server);
                    log.info("Refreshed tools after enabling toolsets, now have {} tools", availableTools.size());
                }
            }

            if (availableTools.isEmpty()) {
                log.warn("No tools available for server: {}", server.getName());
                return "There are no tools available on the selected MCP server.";
            }

            // Select the best tool using intelligent selector
            String toolName = intelligentToolSelector.selectBestTool(userMessage, availableTools, server);
            if (toolName == null) {
                log.warn("No suitable tool found for message: '{}'", userMessage);
                return buildNoSuitableToolResponse(userMessage, availableTools, server);
            }
            
            Map<String, Object> toolSchema = availableTools.stream()
                    .filter(t -> toolName.equals(t.get("name")))
                    .findFirst()
                    .orElse(availableTools.get(0));
            
            // Extract arguments using intelligent selector  
            Map<String, Object> arguments = intelligentToolSelector.extractToolArguments(
                    userMessage, toolName, toolSchema, server);

            log.info("Selected tool '{}' for message: '{}'", toolName, userMessage);

            // Execute the selected tool
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
    
    private boolean isDirectToolsetRequest(String userMessage) {
        if (userMessage == null) return false;
        
        String lower = userMessage.trim().toLowerCase();
        return lower.startsWith("enable toolset") || 
               lower.startsWith("list toolsets") ||
               lower.startsWith("show toolsets") ||
               lower.contains("available toolsets");
    }
    
    private String handleDirectToolsetRequest(McpServer server, String userMessage) {
        String lower = userMessage.trim().toLowerCase();
        
        if (lower.startsWith("enable toolset")) {
            // Extract toolset name
            String[] parts = userMessage.trim().split("\\s+");
            String toolsetName = parts.length > 2 ? parts[2] : null;
            if (toolsetName == null) {
                return "Error: Please specify the toolset name to enable.";
            }
            
            boolean success = dynamicToolsetManager.enableToolset(server, toolsetName);
            return success ? 
                String.format("Successfully enabled toolset '%s'", toolsetName) :
                String.format("Failed to enable toolset '%s'", toolsetName);
        }
        
        if (lower.contains("list") || lower.contains("show") || lower.contains("available")) {
            List<Map<String, Object>> toolsets = dynamicToolsetManager.discoverAvailableToolsets(server);
            return formatToolsetsResponse(toolsets);
        }
        
        return "Unknown toolset request format";
    }
    
    private String formatToolsetsResponse(List<Map<String, Object>> toolsets) {
        if (toolsets.isEmpty()) {
            return "No toolsets available on this server.";
        }
        
        StringBuilder response = new StringBuilder("Available toolsets:\n");
        for (Map<String, Object> toolset : toolsets) {
            String name = (String) toolset.get("name");
            String description = (String) toolset.get("description");
            Boolean enabled = (Boolean) toolset.get("enabled");
            
            response.append(String.format("- %s: %s %s\n", 
                name, 
                description != null ? description : "No description",
                Boolean.TRUE.equals(enabled) ? "(enabled)" : "(disabled)"));
        }
        
        return response.toString();
    }
    
    private List<String> identifyMissingCapabilities(String userMessage, List<Map<String, Object>> availableTools) {
        List<String> missingCapabilities = new ArrayList<>();
        String lower = userMessage.toLowerCase();
        
        // Analyze what the user is trying to do and what capabilities might be missing
        if (containsBranchRelatedIntent(lower) && !hasToolForCapability(availableTools, "branch")) {
            missingCapabilities.add("branches");
        }
        
        if (containsRepositoryRelatedIntent(lower) && !hasToolForCapability(availableTools, "repo")) {
            missingCapabilities.add("repositories");
        }
        
        if (containsFileRelatedIntent(lower) && !hasToolForCapability(availableTools, "file")) {
            missingCapabilities.add("files");
            missingCapabilities.add("content");
        }
        
        if (containsIssueRelatedIntent(lower) && !hasToolForCapability(availableTools, "issue")) {
            missingCapabilities.add("issues");
        }
        
        if (containsPullRequestRelatedIntent(lower) && !hasToolForCapability(availableTools, "pull")) {
            missingCapabilities.add("pull_requests");
        }
        
        return missingCapabilities;
    }
    
    private boolean containsBranchRelatedIntent(String message) {
        return message.contains("branch") || message.contains("rama") ||
               message.contains("branches") || message.contains("ramas");
    }
    
    private boolean containsRepositoryRelatedIntent(String message) {
        return message.contains("repo") || message.contains("repository") ||
               message.contains("repositorio");
    }
    
    private boolean containsFileRelatedIntent(String message) {
        return message.contains("file") || message.contains("archivo") ||
               message.contains("content") || message.contains("contenido");
    }
    
    private boolean containsIssueRelatedIntent(String message) {
        return message.contains("issue") || message.contains("problema") ||
               message.contains("issues");
    }
    
    private boolean containsPullRequestRelatedIntent(String message) {
        return message.contains("pull request") || message.contains("pr") ||
               message.contains("merge request");
    }
    
    private boolean hasToolForCapability(List<Map<String, Object>> tools, String capability) {
        for (Map<String, Object> tool : tools) {
            String toolName = (String) tool.get("name");
            String description = (String) tool.get("description");
            
            if (toolName != null && toolName.toLowerCase().contains(capability)) {
                return true;
            }
            
            if (description != null && description.toLowerCase().contains(capability)) {
                return true;
            }
        }
        return false;
    }
    
    private String buildNoSuitableToolResponse(String userMessage, List<Map<String, Object>> availableTools, McpServer server) {
        StringBuilder response = new StringBuilder();
        response.append("Unable to find a suitable tool for your request: '").append(userMessage).append("'\n\n");
        
        if (!availableTools.isEmpty()) {
            response.append("Available tools on this server:\n");
            for (Map<String, Object> tool : availableTools) {
                String name = (String) tool.get("name");
                String description = (String) tool.get("description");
                response.append("- ").append(name);
                if (description != null && !description.isEmpty()) {
                    response.append(": ").append(description);
                }
                response.append("\n");
            }
            
            response.append("\nTip: Try enabling additional toolsets if available, or rephrase your request to match one of the available tools.");
        }
        
        return response.toString();
    }
}