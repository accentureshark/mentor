package org.shark.mentor.mcp.application.service.tool;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.shark.mentor.mcp.application.service.notification.DynamicToolInfoService;
import org.shark.mentor.mcp.domain.model.McpServer;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Service that caches tool context information to reduce repeated MCP calls
 * and provides enhanced context for LLM prompts
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class ToolContextCache {

    private final DynamicToolInfoService dynamicToolInfoService;
    
    // Cache tool context for 30 minutes
    private final Map<String, CachedToolContext> contextCache = new ConcurrentHashMap<>();
    private static final long CACHE_DURATION_MS = TimeUnit.MINUTES.toMillis(30);

    /**
     * Cached tool context with expiration
     */
    public static class CachedToolContext {
        private final DynamicToolInfoService.ToolContext toolContext;
        private final long timestamp;
        private final String enhancedContextSummary;

        public CachedToolContext(DynamicToolInfoService.ToolContext toolContext, String enhancedContextSummary) {
            this.toolContext = toolContext;
            this.timestamp = System.currentTimeMillis();
            this.enhancedContextSummary = enhancedContextSummary;
        }

        public boolean isExpired() {
            return (System.currentTimeMillis() - timestamp) > CACHE_DURATION_MS;
        }

        public DynamicToolInfoService.ToolContext getToolContext() { return toolContext; }
        public String getEnhancedContextSummary() { return enhancedContextSummary; }
    }

    /**
     * Gets cached tool context or creates it if not available/expired
     */
    public CachedToolContext getToolContext(McpServer server, String contextContent) {
        String cacheKey = server.getId() + "_" + contextContent.hashCode();
        
        CachedToolContext cached = contextCache.get(cacheKey);
        if (cached != null && !cached.isExpired()) {
            log.debug("Using cached tool context for server: {}", server.getName());
            return cached;
        }

        // Create new context analysis
        log.debug("Creating new tool context analysis for server: {}", server.getName());
        DynamicToolInfoService.ToolContext toolContext = dynamicToolInfoService.analyzeToolContext(server, contextContent);
        String enhancedSummary = buildEnhancedContextSummary(toolContext, server, contextContent);
        
        CachedToolContext newContext = new CachedToolContext(toolContext, enhancedSummary);
        contextCache.put(cacheKey, newContext);
        
        // Clean up expired entries periodically
        if (contextCache.size() > 100) {
            cleanupExpiredEntries();
        }
        
        return newContext;
    }

    /**
     * Builds an enhanced context summary that provides more information to the LLM
     * to reduce the need for additional calls
     */
    private String buildEnhancedContextSummary(DynamicToolInfoService.ToolContext toolContext, 
                                             McpServer server, String contextContent) {
        StringBuilder summary = new StringBuilder();
        
        // Add server capability summary
        summary.append("SERVER CAPABILITIES:\n");
        summary.append("Server: ").append(server.getName()).append("\n");
        summary.append("Description: ").append(server.getDescription()).append("\n");
        
        if (!toolContext.getDomains().isEmpty()) {
            summary.append("Domains: ").append(String.join(", ", toolContext.getDomains())).append("\n");
        }
        
        if (!toolContext.getDataTypes().isEmpty()) {
            summary.append("Data Types: ").append(String.join(", ", toolContext.getDataTypes())).append("\n");
        }
        
        if (!toolContext.getOperations().isEmpty()) {
            summary.append("Available Operations: ").append(String.join(", ", toolContext.getOperations())).append("\n");
        }
        
        // Add context analysis
        summary.append("\nCONTEXT ANALYSIS:\n");
        summary.append("Primary Format: ").append(toolContext.getFormatHints().getOrDefault("primary", "general")).append("\n");
        summary.append("Recommended Emoji: ").append(dynamicToolInfoService.getContextEmoji(toolContext)).append("\n");
        
        // Add formatting guidelines
        summary.append("\nFORMATTING GUIDELINES:\n");
        summary.append(dynamicToolInfoService.buildDynamicFormatInstructions(toolContext, server.getName()));
        
        return summary.toString();
    }

    /**
     * Provides a comprehensive context prompt that reduces the need for additional LLM calls
     */
    public String buildComprehensivePrompt(McpServer server, String contextContent, String question) {
        CachedToolContext cachedContext = getToolContext(server, contextContent);
        
        StringBuilder prompt = new StringBuilder();
        
        // Enhanced context with server capabilities
        prompt.append("COMPREHENSIVE MCP SERVER CONTEXT:\n");
        prompt.append(cachedContext.getEnhancedContextSummary());
        
        // Original context content
        prompt.append("\nACTUAL DATA RESPONSE:\n");
        prompt.append(contextContent);
        
        // User question context
        prompt.append("\nUSER QUESTION: ").append(question);
        
        // Additional instructions for comprehensive response
        prompt.append("\n\nRESPONSE REQUIREMENTS:\n");
        prompt.append("1. Provide a complete, self-contained answer using the available data\n");
        prompt.append("2. Include relevant context about the server's capabilities when helpful\n");
        prompt.append("3. Format according to the data type and server domain\n");
        prompt.append("4. If the data is incomplete, explain what additional information could be obtained\n");
        prompt.append("5. End with the server attribution as specified\n");
        
        return prompt.toString();
    }

    /**
     * Clean up expired cache entries
     */
    private void cleanupExpiredEntries() {
        contextCache.entrySet().removeIf(entry -> entry.getValue().isExpired());
        log.debug("Cleaned up expired tool context cache entries. Current size: {}", contextCache.size());
    }

    /**
     * Clear all cached contexts (useful for testing or configuration changes)
     */
    public void clearCache() {
        contextCache.clear();
        log.info("Tool context cache cleared");
    }
}