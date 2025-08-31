package org.shark.mentor.mcp.application.service.llm;

import dev.langchain4j.model.ollama.OllamaChatModel;
import dev.langchain4j.model.chat.ChatLanguageModel;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Factory for creating ChatLanguageModel instances with langchain4j best practices
 * Includes connection pooling and optimization for performance
 */
@Slf4j
public class LlmFactory {
    
    // Cache for reusing model instances to avoid repeated initialization
    private static final ConcurrentHashMap<String, ChatLanguageModel> modelCache = new ConcurrentHashMap<>();
    
    public static ChatLanguageModel createChatModel(String provider, String model, String baseUrl, String apiKey, double temperature, int timeoutMinutes) {
        // Create a cache key based on all parameters
        String cacheKey = String.format("%s:%s:%s:%s:%.2f:%d", provider, model, baseUrl, apiKey, temperature, timeoutMinutes);
        
        // Return cached instance if available
        ChatLanguageModel cachedModel = modelCache.get(cacheKey);
        if (cachedModel != null) {
            log.debug("Reusing cached LLM model for key: {}", cacheKey);
            return cachedModel;
        }
        
        log.info("Creating new LLM model: provider={}, model={}, baseUrl={}, temperature={}, timeout={}min", 
                provider, model, baseUrl, temperature, timeoutMinutes);
        
        ChatLanguageModel newModel;
        
        switch (provider.toLowerCase()) {
            case "ollama":
                newModel = OllamaChatModel.builder()
                        .baseUrl(baseUrl != null ? baseUrl : "http://localhost:11434")
                        .modelName(model)
                        .temperature(temperature)
                        .timeout(Duration.ofMinutes(timeoutMinutes))
                        // Optimize for speed based on model type
                        .numPredict(getOptimalResponseLength(model))
                        .build();
                break;
            
            // Future providers can be added here:
            // case "openai":
            // case "azure-openai":
            // case "anthropic":
            
            default:
                throw new IllegalArgumentException("Unsupported LLM provider: " + provider);
        }
        
        // Cache the model for reuse
        modelCache.put(cacheKey, newModel);
        log.info("Cached new LLM model with key: {}", cacheKey);
        
        return newModel;
    }
    
    // Backward compatibility method for tests
    public static ChatLanguageModel createChatModel(String provider, String model, String baseUrl, String apiKey) {
        return createChatModel(provider, model, baseUrl, apiKey, 0.7, 2);
    }
    
    /**
     * Clear the model cache (useful for testing or configuration changes)
     */
    public static void clearCache() {
        modelCache.clear();
        log.info("Cleared LLM model cache");
    }
    
    /**
     * Get cache statistics for monitoring
     */
    public static int getCacheSize() {
        return modelCache.size();
    }
    
    /**
     * Get optimal response length based on model type for speed optimization
     */
    private static int getOptimalResponseLength(String model) {
        if (model == null) return 2048;
        
        String modelLower = model.toLowerCase();
        
        // Smaller models can use shorter responses for speed
        if (modelLower.contains("gemma2:2b") || modelLower.contains("llama3.2:1b")) {
            return 1024; // Shorter responses for small, fast models
        } else if (modelLower.contains("llama3.2:3b") || modelLower.contains("phi3") || modelLower.contains("qwen2.5:3b")) {
            return 1536; // Medium responses for balanced models
        } else {
            return 2048; // Default for larger models
        }
    }
}
