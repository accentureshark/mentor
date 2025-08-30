package org.shark.mentor.mcp.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(UiProperties.class)
public class AppConfig {
    @Bean
    public UiProperties uiProperties() {
        return new UiProperties();
    }
}

