package org.shark.mentor.mcp;

import org.shark.mentor.mcp.service.LlmService;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

@TestConfiguration
public class IntegrationTestConfig {
    @Bean
    public LlmService llmService() {
        return (prompt, context) -> "test-response";
    }
}
