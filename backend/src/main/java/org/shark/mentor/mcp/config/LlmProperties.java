package org.shark.mentor.mcp.config;

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

    @Data
    public static class Api {
        private String baseUrl;
        private String key;
    }
}