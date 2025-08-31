package org.shark.mentor.mcp.infraestructure.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration class to enable intent keywords properties loading
 */
@Configuration
@EnableConfigurationProperties(IntentKeywordProperties.class)
public class IntentKeywordConfig {
}