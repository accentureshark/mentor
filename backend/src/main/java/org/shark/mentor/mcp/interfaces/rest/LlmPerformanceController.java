package org.shark.mentor.mcp.interfaces.rest;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.shark.mentor.mcp.application.service.llm.LlmServiceEnhanced;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * REST controller for LLM performance monitoring and cache management
 */
@RestController
@RequestMapping("/api/llm")
@RequiredArgsConstructor
@Slf4j
public class LlmPerformanceController {

    private final LlmServiceEnhanced llmServiceEnhanced;

    /**
     * Get LLM cache statistics for performance monitoring
     */
    @GetMapping("/cache/stats")
    public ResponseEntity<Map<String, Object>> getCacheStats() {
        try {
            Map<String, Object> stats = llmServiceEnhanced.getCacheStats();
            log.debug("Retrieved LLM cache stats: {}", stats);
            return ResponseEntity.ok(stats);
        } catch (Exception e) {
            log.error("Error retrieving cache stats: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError()
                .body(Map.of("error", "Failed to retrieve cache stats: " + e.getMessage()));
        }
    }

    /**
     * Clear all LLM caches
     */
    @PostMapping("/cache/clear")
    public ResponseEntity<Map<String, String>> clearCaches() {
        try {
            llmServiceEnhanced.clearAllCaches();
            log.info("Cleared all LLM caches");
            return ResponseEntity.ok(Map.of("message", "All LLM caches cleared successfully"));
        } catch (Exception e) {
            log.error("Error clearing caches: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError()
                .body(Map.of("error", "Failed to clear caches: " + e.getMessage()));
        }
    }

    /**
     * Clear conversation memory for a specific conversation
     */
    @DeleteMapping("/conversations/{conversationId}")
    public ResponseEntity<Map<String, String>> clearConversation(@PathVariable String conversationId) {
        try {
            llmServiceEnhanced.clearConversation(conversationId);
            log.info("Cleared conversation memory for: {}", conversationId);
            return ResponseEntity.ok(Map.of("message", "Conversation memory cleared for: " + conversationId));
        } catch (Exception e) {
            log.error("Error clearing conversation {}: {}", conversationId, e.getMessage(), e);
            return ResponseEntity.internalServerError()
                .body(Map.of("error", "Failed to clear conversation: " + e.getMessage()));
        }
    }
}