package org.shark.mentor.mcp.infraestructure.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "llm")
public class LlmProperties {
    private String provider;
    private String model;
    private Api api = new Api();
    private ModelConfig modelConfig = new ModelConfig();
    private Performance performance = new Performance();

    @Data
    public static class Api {
        private String baseUrl;
        private String key;
    }
    
    @Data
    public static class ModelConfig {
        private double temperature = 0.7;
        private int timeoutMinutes = 2;
        private String defaultLocale = "en";
    }
    
    @Data
    public static class Performance {
        private boolean enableCaching = true;
        private int cacheTtlMinutes = 10;
        private int maxCacheSize = 1000;
        private boolean enableConnectionPooling = true;
        private int maxPoolSize = 10;
        private boolean enableConversationMemoryCleanup = true;
        private int conversationMemoryTtlMinutes = 60;
        private int maxConversationsInMemory = 100;
        // Optimized timeout for faster responses
        private int fastTimeoutSeconds = 30;
        // Enable async processing for non-blocking operations
        private boolean enableAsyncProcessing = true;
    }
}