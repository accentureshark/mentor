package org.shark.alma.mcp.config;

import java.util.ArrayList;
import java.util.List;

import org.shark.alma.mcp.model.McpServer;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import lombok.Data;

/**
 * Configuration properties for MCP servers.
 */
@Configuration
@ConfigurationProperties(prefix = "mcp")
@Data
public class McpProperties {

    /**
     * Configured MCP servers loaded from application.yml.
     */
    private List<McpServer> servers = new ArrayList<>();

    @Data
    public static class ServerConfig {
        private int port;
        private String host;
    }
    private ServerConfig server = new ServerConfig();
}
