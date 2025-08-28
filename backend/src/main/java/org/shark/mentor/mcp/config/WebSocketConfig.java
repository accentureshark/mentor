package org.shark.mentor.mcp.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.shark.mentor.mcp.websocket.McpConfigWebSocketHandler;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final McpConfigWebSocketHandler mcpConfigWebSocketHandler;

    public WebSocketConfig(McpConfigWebSocketHandler mcpConfigWebSocketHandler) {
        this.mcpConfigWebSocketHandler = mcpConfigWebSocketHandler;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(mcpConfigWebSocketHandler, "/ws/mcp-config")
                .setAllowedOrigins("*"); // In production, specify exact origins
    }
}