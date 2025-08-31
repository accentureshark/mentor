package org.shark.mentor.mcp.infraestructure.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({UiProperties.class, PromptProperties.class})
public class AppConfig {
    @Bean
    public UiProperties uiProperties() {
        return new UiProperties();
    }
    
    @Bean
    public PromptProperties promptProperties() {
        return new PromptProperties();
    }
}
