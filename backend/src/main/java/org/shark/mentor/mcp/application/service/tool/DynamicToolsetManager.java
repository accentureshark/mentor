package org.shark.mentor.mcp.application.service.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.shark.mentor.mcp.domain.model.McpServer;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service for managing dynamic toolset discovery and enablement.
 * This service provides intelligent toolset management without hardcoding specific patterns.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DynamicToolsetManager {

    private final McpToolService mcpToolService;
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    // Cache for available toolsets per server
    private final Map<String, List<Map<String, Object>>> toolsetCache = new ConcurrentHashMap<>();
    
    // Cache for enabled toolsets per server
    private final Map<String, Set<String>> enabledToolsetsCache = new ConcurrentHashMap<>();

    /**
     * Discovers and returns available toolsets for a server
     */
    public List<Map<String, Object>> discoverAvailableToolsets(McpServer server) {
        String serverId = server.getId();
        
        // Check cache first
        if (toolsetCache.containsKey(serverId)) {
            log.debug("Returning cached toolsets for server: {}", server.getName());
            return toolsetCache.get(serverId);
        }
        
        log.info("Discovering available toolsets for server: {}", server.getName());
        List<Map<String, Object>> availableTools = mcpToolService.getTools(server);
        
        // Look for toolset management tools
        List<Map<String, Object>> toolsets = new ArrayList<>();
        
        for (Map<String, Object> tool : availableTools) {
            String toolName = (String) tool.get("name");
            if (isToolsetManagementTool(toolName)) {
                try {
                    List<Map<String, Object>> discoveredToolsets = callToolsetDiscoveryTool(server, tool);
                    toolsets.addAll(discoveredToolsets);
                } catch (Exception e) {
                    log.warn("Failed to discover toolsets using tool '{}': {}", toolName, e.getMessage());
                }
            }
        }
        
        // Cache the results
        toolsetCache.put(serverId, toolsets);
        log.info("Discovered {} toolsets for server {}", toolsets.size(), server.getName());
        
        return toolsets;
    }

    /**
     * Intelligently enables toolsets based on user intent
     */
    public boolean enableToolsetsForIntent(McpServer server, String userMessage, List<String> missingCapabilities) {
        log.info("Attempting to enable toolsets for intent: '{}' on server: {}", userMessage, server.getName());
        
        List<Map<String, Object>> availableToolsets = discoverAvailableToolsets(server);
        if (availableToolsets.isEmpty()) {
            log.warn("No toolsets available for server: {}", server.getName());
            return false;
        }
        
        // Analyze which toolsets might help with the user's intent
        List<String> candidateToolsets = analyzeRequiredToolsets(userMessage, missingCapabilities, availableToolsets);
        
        boolean anyEnabled = false;
        for (String toolsetName : candidateToolsets) {
            if (enableToolset(server, toolsetName)) {
                anyEnabled = true;
            }
        }
        
        if (anyEnabled) {
            // Clear the tool cache to force refresh
            invalidateToolCache(server);
        }
        
        return anyEnabled;
    }

    /**
     * Enables a specific toolset
     */
    public boolean enableToolset(McpServer server, String toolsetName) {
        log.info("Enabling toolset '{}' on server: {}", toolsetName, server.getName());
        
        String serverId = server.getId();
        Set<String> enabledToolsets = enabledToolsetsCache.computeIfAbsent(serverId, k -> new HashSet<>());
        
        if (enabledToolsets.contains(toolsetName)) {
            log.debug("Toolset '{}' already enabled on server: {}", toolsetName, server.getName());
            return true;
        }
        
        try {
            // Look for enable_toolset tool or similar
            List<Map<String, Object>> availableTools = mcpToolService.getTools(server);
            Map<String, Object> enableTool = findToolsetEnableTool(availableTools);
            
            if (enableTool == null) {
                log.warn("No toolset enable tool found on server: {}", server.getName());
                return false;
            }
            
            // Call the enable tool
            Map<String, Object> params = Map.of("toolset", toolsetName);
            String result = callEnableToolsetTool(server, enableTool, params);
            
            // Parse result to check if successful
            if (isSuccessfulEnableResult(result)) {
                enabledToolsets.add(toolsetName);
                log.info("Successfully enabled toolset '{}' on server: {}", toolsetName, server.getName());
                return true;
            } else {
                log.warn("Failed to enable toolset '{}' on server: {}: {}", toolsetName, server.getName(), result);
                return false;
            }
            
        } catch (Exception e) {
            log.error("Error enabling toolset '{}' on server {}: {}", toolsetName, server.getName(), e.getMessage(), e);
            return false;
        }
    }

    /**
     * Invalidates caches for a server (useful after enabling toolsets)
     */
    public void invalidateToolCache(McpServer server) {
        String serverId = server.getId();
        toolsetCache.remove(serverId);
        log.debug("Invalidated tool cache for server: {}", server.getName());
    }

    /**
     * Checks if tool capabilities match user intent
     */
    public boolean hasCapabilityForIntent(List<Map<String, Object>> tools, String userMessage) {
        if (tools == null || tools.isEmpty()) {
            return false;
        }
        
        String lowerMessage = userMessage.toLowerCase();
        
        // Check if any tool seems relevant to the user's intent
        for (Map<String, Object> tool : tools) {
            String toolName = (String) tool.get("name");
            String description = (String) tool.get("description");
            
            if (toolName != null && isToolRelevantToIntent(toolName, description, lowerMessage)) {
                return true;
            }
        }
        
        return false;
    }

    private boolean isToolsetManagementTool(String toolName) {
        if (toolName == null) return false;
        
        String lower = toolName.toLowerCase();
        return lower.contains("list_available_toolsets") ||
               lower.contains("available_toolsets") ||
               lower.contains("list_toolsets") ||
               lower.contains("toolsets");
    }

    private List<Map<String, Object>> callToolsetDiscoveryTool(McpServer server, Map<String, Object> tool) throws Exception {
        String toolName = (String) tool.get("name");
        
        try {
            String result = mcpToolService.callToolViaHttp(server, toolName, Map.of());
            return parseToolsetResult(result);
        } catch (Exception e) {
            // Try stdio if HTTP fails
            try {
                String result = mcpToolService.callMcpMethodViaHttp(server, toolName, Map.of());
                return parseToolsetResult(result);
            } catch (Exception e2) {
                log.warn("Failed to call toolset discovery tool '{}' via both HTTP and stdio", toolName);
                throw e2;
            }
        }
    }

    private List<Map<String, Object>> parseToolsetResult(String result) {
        try {
            JsonNode resultNode = objectMapper.readTree(result);
            
            // Handle different response formats
            if (resultNode.has("result")) {
                JsonNode actualResult = resultNode.get("result");
                if (actualResult.has("toolsets")) {
                    return objectMapper.convertValue(actualResult.get("toolsets"), List.class);
                } else if (actualResult.isArray()) {
                    return objectMapper.convertValue(actualResult, List.class);
                }
            } else if (resultNode.isArray()) {
                return objectMapper.convertValue(resultNode, List.class);
            }
            
        } catch (Exception e) {
            log.warn("Failed to parse toolset result: {}", result, e);
        }
        
        return new ArrayList<>();
    }

    private List<String> analyzeRequiredToolsets(String userMessage, List<String> missingCapabilities, 
                                                List<Map<String, Object>> availableToolsets) {
        List<String> candidates = new ArrayList<>();
        String lowerMessage = userMessage.toLowerCase();
        
        // Analyze user intent and match with toolset capabilities
        for (Map<String, Object> toolset : availableToolsets) {
            String toolsetName = (String) toolset.get("name");
            String description = (String) toolset.get("description");
            Boolean enabled = (Boolean) toolset.get("enabled");
            
            if (Boolean.TRUE.equals(enabled)) {
                continue; // Already enabled
            }
            
            if (isToolsetRelevantToIntent(toolsetName, description, lowerMessage, missingCapabilities)) {
                candidates.add(toolsetName);
            }
        }
        
        return candidates;
    }

    private boolean isToolsetRelevantToIntent(String toolsetName, String description, 
                                            String lowerMessage, List<String> missingCapabilities) {
        if (toolsetName == null) return false;
        
        String lowerToolsetName = toolsetName.toLowerCase();
        String lowerDescription = description != null ? description.toLowerCase() : "";
        
        // Check for direct mentions
        if (lowerMessage.contains(lowerToolsetName)) {
            return true;
        }
        
        // Check for GitHub-related intent
        if (containsGitHubIntent(lowerMessage) && 
            (lowerToolsetName.contains("github") || lowerDescription.contains("github"))) {
            return true;
        }
        
        // Check for repository-related intent
        if (containsRepositoryIntent(lowerMessage) && 
            (lowerToolsetName.contains("repo") || lowerDescription.contains("repo"))) {
            return true;
        }
        
        // Check against missing capabilities
        for (String capability : missingCapabilities) {
            if (lowerToolsetName.contains(capability.toLowerCase()) || 
                lowerDescription.contains(capability.toLowerCase())) {
                return true;
            }
        }
        
        return false;
    }

    private boolean containsGitHubIntent(String message) {
        return message.contains("github") || message.contains("git") || 
               message.contains("branch") || message.contains("commit") ||
               message.contains("pull request") || message.contains("pr") ||
               message.contains("issue") || message.contains("repository") ||
               message.contains("repo");
    }

    private Map<String, Object> findToolsetEnableTool(List<Map<String, Object>> tools) {
        for (Map<String, Object> tool : tools) {
            String toolName = (String) tool.get("name");
            if (toolName != null && 
                (toolName.equals("enable_toolset") || toolName.contains("enable"))) {
                return tool;
            }
        }
        return null;
    }

    private String callEnableToolsetTool(McpServer server, Map<String, Object> tool, Map<String, Object> params) throws Exception {
        String toolName = (String) tool.get("name");
        
        try {
            return mcpToolService.callToolViaHttp(server, toolName, params);
        } catch (Exception e) {
            // Try generic MCP method call
            return mcpToolService.callMcpMethodViaHttp(server, toolName, params);
        }
    }

    private boolean isSuccessfulEnableResult(String result) {
        if (result == null) return false;
        
        try {
            JsonNode resultNode = objectMapper.readTree(result);
            
            // Check for error
            if (resultNode.has("error")) {
                return false;
            }
            
            // Check for success indicators
            if (resultNode.has("result")) {
                JsonNode actualResult = resultNode.get("result");
                if (actualResult.has("success")) {
                    return actualResult.get("success").asBoolean();
                }
                // If no explicit success field, assume success if no error
                return true;
            }
            
            // If no result field, check response content
            String resultText = result.toLowerCase();
            return !resultText.contains("error") && !resultText.contains("failed");
            
        } catch (Exception e) {
            log.warn("Failed to parse enable result: {}", result, e);
            return false;
        }
    }

    private boolean isToolRelevantToIntent(String toolName, String description, String lowerMessage) {
        if (toolName == null) return false;
        
        String lowerToolName = toolName.toLowerCase();
        String lowerDescription = description != null ? description.toLowerCase() : "";
        
        // Check for direct tool name mention
        if (lowerMessage.contains(lowerToolName.replace("_", " ")) || 
            lowerMessage.contains(lowerToolName)) {
            return true;
        }
        
        // Check for semantic matches - both intent AND entity must match
        if (containsBranchIntent(lowerMessage) && 
            (lowerToolName.contains("branch") || lowerDescription.contains("branch"))) {
            return true;
        }
        
        if (containsRepositoryIntent(lowerMessage) && 
            (lowerToolName.contains("repo") || lowerDescription.contains("repo"))) {
            return true;
        }
        
        if (containsFileIntent(lowerMessage) && 
            (lowerToolName.contains("file") || lowerDescription.contains("file") ||
             lowerToolName.contains("content") || lowerDescription.contains("content"))) {
            return true;
        }
        
        if (containsIssueIntent(lowerMessage) && 
            (lowerToolName.contains("issue") || lowerDescription.contains("issue"))) {
            return true;
        }
        
        // Only match list intent if the entity also matches
        if (containsListIntent(lowerMessage)) {
            if (containsBranchIntent(lowerMessage) && 
                (lowerToolName.contains("branch") || lowerDescription.contains("branch"))) {
                return true;
            }
            if (containsRepositoryIntent(lowerMessage) && 
                (lowerToolName.contains("repo") || lowerDescription.contains("repo"))) {
                return true;
            }
            if (containsFileIntent(lowerMessage) && 
                (lowerToolName.contains("file") || lowerDescription.contains("file"))) {
                return true;
            }
            if (containsIssueIntent(lowerMessage) && 
                (lowerToolName.contains("issue") || lowerDescription.contains("issue"))) {
                return true;
            }
        }
        
        return false;
    }

    private boolean containsBranchIntent(String message) {
        return message.contains("branch") || message.contains("rama") ||
               message.contains("branches") || message.contains("ramas");
    }

    private boolean containsRepositoryIntent(String message) {
        return message.contains("repo") || message.contains("repository") ||
               message.contains("repositorio");
    }

    private boolean containsFileIntent(String message) {
        return message.contains("file") || message.contains("archivo") ||
               message.contains("content") || message.contains("contenido");
    }

    private boolean containsIssueIntent(String message) {
        return message.contains("issue") || message.contains("problema") ||
               message.contains("issues");
    }

    private boolean containsListIntent(String message) {
        return message.contains("list") || message.contains("listar") ||
               message.contains("mostrar") || message.contains("ver") ||
               message.contains("todos") || message.contains("all");
    }
}