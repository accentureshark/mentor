package org.shark.mentor.mcp;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

import org.shark.mentor.mcp.config.McpProperties;

/**
 * Main application class for MCP Client Backend
 */
@SpringBootApplication
@EnableConfigurationProperties(McpProperties.class)
public class McpClientApplication {

    public static void main(String[] args) {
        SpringApplication.run(McpClientApplication.class, args);
    }
}
