package org.shark.mentor.mcp.service;

import dev.langchain4j.model.ollama.OllamaChatModel;
import dev.langchain4j.model.chat.ChatLanguageModel;
import lombok.extern.slf4j.Slf4j;

/**
 * Factory for creating ChatLanguageModel instances with langchain4j best practices
 */
@Slf4j
public class LlmFactory {
    
    public static ChatLanguageModel createChatModel(String provider, String model, String baseUrl, String apiKey, double temperature, int timeoutMinutes) {
        log.info("Creating LLM model: provider={}, model={}, baseUrl={}, temperature={}, timeout={}min", 
                provider, model, baseUrl, temperature, timeoutMinutes);
        
        switch (provider.toLowerCase()) {
            case "ollama":
                return OllamaChatModel.builder()
                        .baseUrl(baseUrl != null ? baseUrl : "http://localhost:11434")
                        .modelName(model)
                        .temperature(temperature)
                        .timeout(java.time.Duration.ofMinutes(timeoutMinutes))
                        .build();
            
            // Future providers can be added here:
            // case "openai":
            // case "azure-openai":
            // case "anthropic":
            
            default:
                throw new IllegalArgumentException("Unsupported LLM provider: " + provider);
        }
    }
    
    // Backward compatibility method for tests
    public static ChatLanguageModel createChatModel(String provider, String model, String baseUrl, String apiKey) {
        return createChatModel(provider, model, baseUrl, apiKey, 0.7, 2);
    }
}
