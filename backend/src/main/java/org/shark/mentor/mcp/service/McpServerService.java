package org.shark.mentor.mcp.service;

import org.shark.mentor.mcp.config.McpProperties;
import org.shark.mentor.mcp.model.McpServer;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.net.*;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Slf4j
public class McpServerService {

    private final Map<String, McpServer> servers = new ConcurrentHashMap<>();
    private final McpProperties properties;
    private final RestTemplate restTemplate;
    private final HttpClient httpClient;

    public McpServerService(McpProperties properties) {
        this.properties = properties;
        this.restTemplate = new RestTemplate();
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();

        loadServersFromConfig();
    }

    private void loadServersFromConfig() {
        if (properties.getServers() != null && !properties.getServers().isEmpty()) {
            log.info("Loading {} servers from configuration", properties.getServers().size());

            for (McpProperties.ServerConfig serverConfig : properties.getServers()) {
                McpServer server = new McpServer(
                        serverConfig.getId(),
                        serverConfig.getName(),
                        serverConfig.getDescription(),
                        serverConfig.getUrl(),
                        "DISCONNECTED"
                );

                addServer(server);
                log.info("Loaded server from config: {} ({})", server.getName(), server.getUrl());
            }
        } else {
            log.warn("No servers configured in application.yml, falling back to sample servers");
            initializeSampleServers();
        }
    }

    private void initializeSampleServers() {
        // Este método ya no es necesario - se mantiene solo como fallback
        log.info("Using fallback sample servers");

        addServer(new McpServer("sample-server", "Sample MCP Server",
                "Fallback server when no configuration is found",
                "stdio://echo", "DISCONNECTED"));
    }

    public List<McpServer> getAllServers() {
        return new ArrayList<>(servers.values());
    }

    public Optional<McpServer> getServer(String id) {
        return Optional.ofNullable(servers.get(id));
    }

    public McpServer addServer(McpServer server) {
        servers.put(server.getId(), server);
        log.info("Added MCP server: {} at {}", server.getName(), server.getUrl());
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
        if (server == null) {
            return null;
        }

        log.info("Updating server {} status from {} to {}", server.getName(), server.getStatus(), status);
        server.setStatus(status);
        server.setLastConnected(System.currentTimeMillis());
        return server;
    }

    public McpServer connectToServer(String id) {
        McpServer server = servers.get(id);
        if (server == null) {
            throw new IllegalArgumentException("Server not found: " + id);
        }

        log.info("Attempting to connect to server: {} at {}", server.getName(), server.getUrl());

        String protocol = extractProtocol(server.getUrl());
        log.info("Detected protocol: {}", protocol);

        try {
            switch (protocol.toLowerCase()) {
                case "stdio":
                    return connectStdio(server);
                case "http":
                case "https":
                    return connectHttp(server);
                case "ws":
                case "wss":
                    return connectWebSocket(server);
                case "tcp":
                    return connectTcp(server);
                default:
                    log.warn("Unsupported protocol: {} for server: {}", protocol, server.getName());
                    server.setStatus("ERROR");
                    server.setLastConnected(System.currentTimeMillis());
                    return server;
            }
        } catch (Exception e) {
            log.error("Connection failed for server {}: {}", server.getName(), e.getMessage());
            server.setStatus("ERROR");
            server.setLastConnected(System.currentTimeMillis());
            return server;
        }
    }

    private String extractProtocol(String url) {
        if (url == null || !url.contains("://")) {
            return "unknown";
        }
        return url.substring(0, url.indexOf("://"));
    }

    private McpServer connectStdio(McpServer server) {
        log.info("Attempting stdio connection to: {}", server.getName());

        String command = server.getUrl().substring("stdio://".length());
        if (command.trim().isEmpty()) {
            server.setStatus("ERROR");
            throw new RuntimeException("Invalid stdio command");
        }

        try {
            String[] parts = command.split("\\s+");
            String executable = parts[0];

            if (executable.equals("java")) {
                ProcessBuilder pb = new ProcessBuilder("java", "-version");
                Process process = pb.start();
                int exitCode = process.waitFor();

                if (exitCode == 0) {
                    log.info("Java found, stdio connection validated for {}", server.getName());
                    server.setStatus("CONNECTED");
                } else {
                    server.setStatus("ERROR");
                    throw new RuntimeException("Java not available");
                }
            } else {
                log.info("Stdio command validated for {}: {}", server.getName(), command);
                server.setStatus("CONNECTED");
            }
        } catch (Exception e) {
            log.error("Stdio connection error to {}: {}", server.getName(), e.getMessage());
            server.setStatus("ERROR");
            throw new RuntimeException("Stdio connection failed: " + e.getMessage());
        }

        server.setLastConnected(System.currentTimeMillis());
        return server;
    }

    private McpServer connectHttp(McpServer server) {
        log.info("Attempting HTTP connection to: {}", server.getUrl());

        try {
            // Intentar primero /health, luego la raíz si falla
            String healthUrl = server.getUrl() + "/health";
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(healthUrl))
                    .timeout(Duration.ofSeconds(5))
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                log.info("HTTP connection successful to {}", server.getUrl());
                server.setStatus("CONNECTED");
            } else if (response.statusCode() == 404 || response.statusCode() == 400) {
                // Si /health no existe, intentar la raíz
                log.info("Health endpoint not found, trying root endpoint for {}", server.getUrl());
                HttpRequest rootRequest = HttpRequest.newBuilder()
                        .uri(URI.create(server.getUrl()))
                        .timeout(Duration.ofSeconds(5))
                        .GET()
                        .build();

                HttpResponse<String> rootResponse = httpClient.send(rootRequest, HttpResponse.BodyHandlers.ofString());

                if (rootResponse.statusCode() >= 200 && rootResponse.statusCode() < 300) {
                    log.info("HTTP connection successful to root endpoint {}", server.getUrl());
                    server.setStatus("CONNECTED");
                } else {
                    log.warn("HTTP connection returned status {} for {}", rootResponse.statusCode(), server.getUrl());
                    server.setStatus("ERROR");
                }
            } else {
                log.warn("HTTP connection returned status {} for {}", response.statusCode(), server.getUrl());
                server.setStatus("ERROR");
            }
        } catch (Exception e) {
            log.error("HTTP connection error to {}: {}", server.getName(), e.getMessage());
            server.setStatus("ERROR");
        }

        server.setLastConnected(System.currentTimeMillis());
        return server;
    }

    private McpServer connectWebSocket(McpServer server) {
        log.info("Attempting WebSocket connection to: {}", server.getUrl());

        try {
            URI uri = URI.create(server.getUrl());
            String host = uri.getHost();
            int port = uri.getPort();

            if (port == -1) {
                port = uri.getScheme().equals("wss") ? 443 : 80;
            }

            try (Socket socket = new Socket()) {
                socket.connect(new InetSocketAddress(host, port), 5000);
                log.info("WebSocket TCP connection successful to {}:{}", host, port);
                server.setStatus("CONNECTED");
            }
        } catch (Exception e) {
            log.error("WebSocket connection error to {}: {}", server.getName(), e.getMessage());
            server.setStatus("ERROR");
        }

        server.setLastConnected(System.currentTimeMillis());
        return server;
    }

    private McpServer connectTcp(McpServer server) {
        log.info("Attempting TCP connection to: {}", server.getUrl());

        try {
            URI uri = URI.create(server.getUrl());
            String host = uri.getHost();
            int port = uri.getPort();

            try (Socket socket = new Socket()) {
                socket.connect(new InetSocketAddress(host, port), 5000);
                log.info("TCP connection successful to {}:{}", host, port);
                server.setStatus("CONNECTED");
            }
        } catch (Exception e) {
            log.error("TCP connection error to {}: {}", server.getName(), e.getMessage());
            server.setStatus("ERROR");
        }

        server.setLastConnected(System.currentTimeMillis());
        return server;
    }

    public McpServer disconnectFromServer(String id) {
        McpServer server = servers.get(id);
        if (server == null) {
            throw new IllegalArgumentException("Server not found: " + id);
        }

        log.info("Disconnecting from server: {}", server.getName());
        server.setStatus("DISCONNECTED");
        server.setLastConnected(System.currentTimeMillis());
        return server;
    }

    public long getConnectedServersCount() {
        return servers.values().stream()
                .filter(server -> "CONNECTED".equals(server.getStatus()))
                .count();
    }

    public boolean isServerConnected(String id) {
        return getServer(id)
                .map(server -> "CONNECTED".equals(server.getStatus()))
                .orElse(false);
    }
}