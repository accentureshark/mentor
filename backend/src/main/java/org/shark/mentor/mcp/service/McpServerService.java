package org.shark.mentor.mcp.service;

import org.shark.mentor.mcp.config.McpProperties;
import org.shark.mentor.mcp.model.McpServer;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

import java.io.*;
import java.net.*;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;

@Service
@Slf4j
public class McpServerService {

    // Agrega estos campos para manejar los procesos y streams
    private final Map<String, Process> stdioProcesses = new ConcurrentHashMap<>();
    private final Map<String, OutputStream> stdioInputs = new ConcurrentHashMap<>();
    private final Map<String, InputStream> stdioOutputs = new ConcurrentHashMap<>();


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

        loadServersFromJson();
        loadServersFromConfig();
    }

    private void loadServersFromJson() {
        ObjectMapper mapper = new ObjectMapper();
        try {
            var resource = getClass().getClassLoader().getResourceAsStream("mcp-servers.json");
            if (resource == null) {
                log.warn("mcp-servers.json not found in classpath");
                return;
            }
            JsonNode root = mapper.readTree(resource);
            List<McpProperties.ServerConfig> configs = new ArrayList<>();
            for (JsonNode node : root.path("servers")) {
                configs.add(mapper.treeToValue(node, McpProperties.ServerConfig.class));
            }
            if (!configs.isEmpty()) {
                properties.setServers(configs);
                log.info("Loaded {} servers from JSON configuration", configs.size());
            }
        } catch (IOException e) {
            log.error("Failed to load servers from JSON", e);
        }
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

                if (serverConfig.isPrewarm() && "stdio".equalsIgnoreCase(extractProtocol(server.getUrl()))) {
                    try {
                        startStdioProcess(server);
                        log.info("Prewarmed stdio server: {}", server.getName());
                    } catch (Exception e) {
                        log.error("Failed to prewarm server {}: {}", server.getName(), e.getMessage());
                    }
                }
            }
        } else {
            log.warn("No MCP servers configured, falling back to sample servers");
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
                    server.setLastError("Unsupported protocol: " + protocol);
                    return server;
            }
        } catch (Exception e) {
            String errorMsg = e.getClass().getSimpleName() + ": " + (e.getMessage() != null ? e.getMessage() : "Connection failed");
            log.error("Connection failed for server {}: {}", server.getName(), errorMsg, e);
            server.setStatus("ERROR");
            server.setLastConnected(System.currentTimeMillis());
            server.setLastError(errorMsg);
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
        log.info("Conectando vía stdio a: {}", server.getName());
        Process existing = stdioProcesses.get(server.getId());

        try {
            if (existing == null || !existing.isAlive()) {
                if (existing != null) {
                    stdioProcesses.remove(server.getId());
                    stdioInputs.remove(server.getId());
                    stdioOutputs.remove(server.getId());
                }
                startStdioProcess(server);
            } else {
                log.info("Reutilizando proceso stdio existente para {}", server.getName());
            }

            server.setStatus("CONNECTED");
            server.setLastConnected(System.currentTimeMillis());
            server.setLastError(null);
            log.info("Conexión stdio establecida con {}", server.getName());
        } catch (Exception e) {
            String errorMsg = e.getClass().getSimpleName() + ": " + (e.getMessage() != null ? e.getMessage() : "Connection failed");
            log.error("Error conectando stdio a {}: {}", server.getName(), errorMsg, e);
            server.setStatus("ERROR");
            server.setLastConnected(System.currentTimeMillis());
            server.setLastError(errorMsg);
            throw new RuntimeException("Fallo conexión stdio: " + errorMsg);
        }
        return server;
    }

    private void startStdioProcess(McpServer server) throws IOException {
        String command = server.getUrl().substring("stdio://".length()).trim();
        if (command.isEmpty()) {
            throw new RuntimeException("Comando stdio vacío");
        }

        String[] parts = command.split("\\s+");
        ProcessBuilder pb = new ProcessBuilder(parts);
        pb.redirectErrorStream(true);
        Process process = pb.start();

        stdioProcesses.put(server.getId(), process);
        stdioInputs.put(server.getId(), process.getOutputStream());
        InputStream stdout = process.getInputStream();
        stdioOutputs.put(server.getId(), stdout);

        if (stdout.available() > 0) {
            BufferedReader reader = new BufferedReader(new InputStreamReader(stdout));
            String firstLine = reader.readLine();
            log.info("Primer output stdio: {}", firstLine);
        } else {
            log.debug("STDIO server started without initial output");
        }
    }

    private McpServer connectHttp(McpServer server) {
        log.info("Attempting HTTP connection to: {}", server.getUrl());

        try {
            // Intentar primero /health, luego la raíz si falla
            String healthUrl = server.getUrl() + "/mcp/health";
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(healthUrl))
                    .timeout(Duration.ofSeconds(5))
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                log.info("HTTP connection successful to {}", server.getUrl());
                server.setStatus("CONNECTED");
                server.setLastError(null);
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
                    server.setLastError(null);
                } else {
                    String errorMsg = String.format("HTTP %d from root endpoint", rootResponse.statusCode());
                    log.warn("HTTP connection returned status {} for {}", rootResponse.statusCode(), server.getUrl());
                    server.setStatus("ERROR");
                    server.setLastError(errorMsg);
                }
            } else {
                String errorMsg = String.format("HTTP %d from health endpoint", response.statusCode());
                log.warn("HTTP connection returned status {} for {}", response.statusCode(), server.getUrl());
                server.setStatus("ERROR");
                server.setLastError(errorMsg);
            }
        } catch (Exception e) {
            String errorMsg = e.getClass().getSimpleName() + ": " + (e.getMessage() != null ? e.getMessage() : "Connection failed");
            log.error("HTTP connection error to {}: {}", server.getName(), errorMsg, e);
            server.setStatus("ERROR");
            server.setLastError(errorMsg);
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
    // Agrega estos métodos públicos en McpServerService.java

    public Process getStdioProcess(String serverId) {
        return stdioProcesses.get(serverId);
    }

    public OutputStream getStdioInput(String serverId) {
        return stdioInputs.get(serverId);
    }

    public InputStream getStdioOutput(String serverId) {
        return stdioOutputs.get(serverId);
    }

    public boolean pingServer(String id) {
        McpServer server = servers.get(id);
        if (server == null) {
            throw new IllegalArgumentException("Server not found: " + id);
        }

        String protocol = extractProtocol(server.getUrl());
        try {
            switch (protocol.toLowerCase()) {
                case "stdio":
                    return pingStdio(server);
                case "tcp":
                case "ws":
                case "wss":
                    URI uri = URI.create(server.getUrl());
                    String host = uri.getHost();
                    int port = uri.getPort();
                    if (port == -1) port = 80;
                    try (Socket socket = new Socket()) {
                        socket.connect(new InetSocketAddress(host, port), 2000);
                        return true;
                    }
                default:
                    HttpRequest request = HttpRequest.newBuilder()
                            .uri(URI.create(server.getUrl() + "/mcp/ping"))
                            .timeout(Duration.ofSeconds(3))
                            .GET()
                            .build();
                    HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                    return response.statusCode() >= 200 && response.statusCode() < 300;
            }
        } catch (Exception e) {
            log.warn("Ping failed for {}: {}", server.getName(), e.getMessage());
            return false;
        }
    }

    private boolean pingStdio(McpServer server) throws Exception {
        OutputStream stdin = stdioInputs.get(server.getId());
        InputStream stdout = stdioOutputs.get(server.getId());
        if (stdin == null || stdout == null) {
            return false;
        }

        Map<String, Object> request = Map.of(
                "jsonrpc", "2.0",
                "id", UUID.randomUUID().toString(),
                "method", "ping",
                "params", Collections.emptyMap()
        );

        String json = new ObjectMapper().writeValueAsString(request);
        stdin.write((json + "\n").getBytes());
        stdin.flush();

        BufferedReader reader = new BufferedReader(new InputStreamReader(stdout));
        ExecutorService executor = Executors.newSingleThreadExecutor();
        long start = System.currentTimeMillis();
        Future<String> future = executor.submit(reader::readLine);
        try {
            String line = future.get(properties.getPing().getTimeoutMs(), TimeUnit.MILLISECONDS);
            long elapsed = System.currentTimeMillis() - start;
            if (elapsed > properties.getPing().getWarnThresholdMs()) {
                log.warn("Ping response from {} took {} ms (threshold {} ms)",
                        server.getName(), elapsed, properties.getPing().getWarnThresholdMs());
            }
            return line != null && line.toLowerCase().contains("pong");
        } catch (TimeoutException e) {
            future.cancel(true);
            log.warn("Ping to {} timed out after {} ms", server.getName(), properties.getPing().getTimeoutMs());
            reconnectStdio(server);
            return false;
        } finally {
            executor.shutdownNow();
        }
    }

    private void reconnectStdio(McpServer server) {
        log.info("Reconnecting stdio server {}", server.getName());
        Process process = stdioProcesses.remove(server.getId());
        if (process != null && process.isAlive()) {
            process.destroyForcibly();
        }
        stdioInputs.remove(server.getId());
        stdioOutputs.remove(server.getId());
        try {
            connectStdio(server);
        } catch (Exception e) {
            log.error("Failed to reconnect stdio server {}: {}", server.getName(), e.getMessage());
        }
    }

}