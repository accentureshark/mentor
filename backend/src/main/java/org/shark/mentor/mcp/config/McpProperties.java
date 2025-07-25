package org.shark.mentor.mcp.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@Data
@ConfigurationProperties(prefix = "mcp")
public class McpProperties {

    private Server server = new Server();
    private List<ServerConfig> servers;

    @Data
    public static class Server {
        private int port = 3000;
        private String host = "localhost";
    }

    @Data
    public static class ServerConfig {
        private String id;
        private String name;
        private String description;
        private String url;
        private boolean implemented;

    }
}