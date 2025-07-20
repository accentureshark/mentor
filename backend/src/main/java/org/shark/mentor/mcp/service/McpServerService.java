package org.shark.mentor.mcp.service;

import org.shark.mentor.mcp.config.McpProperties;
import org.shark.mentor.mcp.model.McpServer;
import org.springframework.stereotype.Service;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service for managing MCP server registrations
 */
@Service
@Slf4j
public class McpServerService {

    private final Map<String, McpServer> servers = new ConcurrentHashMap<>();
    private final McpProperties properties;

    public McpServerService(McpProperties properties) {
        this.properties = properties;
        loadServersFromConfig();
    }

    private void loadServersFromConfig() {
        List<McpServer> configured = properties.getServers();
        if (configured == null || configured.isEmpty()) {
            initializeSampleServers();
            return;
        }
        configured.forEach(server -> {
            if (server.getStatus() == null) {
                server.setStatus("DISCONNECTED");
            }
            if (server.getLastConnected() == null) {
                server.setLastConnected(System.currentTimeMillis());
            }
            addServer(server);
        });
    }

    private void initializeSampleServers() {
        // Local Melian MCP Server
        addServer(McpServer.builder()
            .id("melian-local")
            .name("Melian MCP Server (Local)")
            .description("Local MELIAN - Módulo de Embedding y Lógica Inteligente para Acceso Natural. Provides movie data from TMDB, SQL databases, and MongoDB.")
            .url("stdio://java -jar ../target/melian-*.jar")
            .status("DISCONNECTED")
            .lastConnected(System.currentTimeMillis() - 60000)
            .build());
            
        // GitHub MCP Server (public example)
        addServer(McpServer.builder()
            .id("github-mcp")
            .name("GitHub MCP Server")
            .description("Provides access to GitHub repositories, issues, and pull requests")
            .url("stdio://npx @modelcontextprotocol/server-github")
            .status("DISCONNECTED")
            .lastConnected(System.currentTimeMillis() - 300000)
            .build());
            
        // File System MCP Server (public example)
        addServer(McpServer.builder()
            .id("filesystem-mcp")
            .name("File System MCP Server")
            .description("Provides secure access to local file system operations")
            .url("stdio://npx @modelcontextprotocol/server-filesystem")
            .status("DISCONNECTED")
            .lastConnected(System.currentTimeMillis() - 120000)
            .build());
            
        // Web Search MCP Server (public example)
        addServer(McpServer.builder()
            .id("brave-search-mcp")
            .name("Brave Search MCP Server")
            .description("Provides web search capabilities through Brave Search API")
            .url("stdio://npx @modelcontextprotocol/server-brave-search")
            .status("DISCONNECTED")
            .lastConnected(System.currentTimeMillis() - 240000)
            .build());
            
        // SQLite MCP Server (public example)
        addServer(McpServer.builder()
            .id("sqlite-mcp")
            .name("SQLite MCP Server")
            .description("Provides database query capabilities for SQLite databases")
            .url("stdio://npx @modelcontextprotocol/server-sqlite")
            .status("DISCONNECTED")
            .lastConnected(System.currentTimeMillis() - 180000)
            .build());
    }
    
    public List<McpServer> getAllServers() {
        return new ArrayList<>(servers.values());
    }
    
    public Optional<McpServer> getServer(String id) {
        return Optional.ofNullable(servers.get(id));
    }
    
    public McpServer addServer(McpServer server) {
        if (server.getId() == null) {
            server.setId(UUID.randomUUID().toString());
        }
        servers.put(server.getId(), server);
        log.info("Added MCP server: {}", server.getName());
        return server;
    }
    
    public boolean removeServer(String id) {
        McpServer removed = servers.remove(id);
        if (removed != null) {
            log.info("Removed MCP server: {}", removed.getName());
            return true;
        }
        return false;
    }
    
    public McpServer updateServerStatus(String id, String status) {
        McpServer server = servers.get(id);
        if (server != null) {
            server.setStatus(status);
            server.setLastConnected(System.currentTimeMillis());
            log.info("Updated server {} status to {}", server.getName(), status);
        }
        return server;
    }
    
    public McpServer connectToServer(String id) {
        McpServer server = servers.get(id);
        if (server == null) {
            log.error("Server with id {} not found", id);
            return null;
        }
        
        log.info("Attempting to connect to server: {} at {}", server.getName(), server.getUrl());
        
        // For melian-local server, try to connect to localhost:3000
        if ("melian-local".equals(id)) {
            try {
                // Simple HTTP check to see if the server is running
                java.net.URL url = new java.net.URL("http://" + properties.getServer().getHost() + ":" + properties.getServer().getPort());
                java.net.HttpURLConnection connection = (java.net.HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                connection.setConnectTimeout(5000); // 5 seconds timeout
                connection.setReadTimeout(5000);
                
                int responseCode = connection.getResponseCode();
                if (responseCode >= 200 && responseCode < 400) {
                    server.setStatus("CONNECTED");
                    server.setLastConnected(System.currentTimeMillis());
                    log.info("Successfully connected to melian server at {}:{}", 
                            properties.getServer().getHost(), properties.getServer().getPort());
                } else {
                    server.setStatus("ERROR");
                    log.warn("Melian server responded with status code: {}", responseCode);
                }
                connection.disconnect();
            } catch (java.io.IOException e) {
                server.setStatus("ERROR");
                log.error("Failed to connect to melian server: {}", e.getMessage());
                throw new RuntimeException("Connection failed: " + e.getMessage());
            }
        } else {
            // For other servers, we'll simulate connection for now
            // In a real implementation, you would implement stdio protocol handling
            log.info("Connection not implemented for stdio protocol servers yet. Setting as DISCONNECTED.");
            server.setStatus("DISCONNECTED");
        }
        
        return server;
    }
}
