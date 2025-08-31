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
}