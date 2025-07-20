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
}
