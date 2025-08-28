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
        // This method is no longer necessary - retained only as fallback
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
                case "unknown":
                    String errorMsg = "Invalid URL format: " + server.getUrl();
                    log.warn("Invalid URL for server: {}", server.getName());
                    server.setStatus("ERROR");
                    server.setLastConnected(System.currentTimeMillis());
                    server.setLastError(errorMsg);
                    return server;
                default:
                    log.warn("Unsupported protocol: {} for server: {}", protocol, server.getName());
                    server.setStatus("ERROR");
                    server.setLastConnected(System.currentTimeMillis());
                    server.setLastError("Unsupported protocol: " + protocol);
                    return server;
            }
        } catch (Exception e) {
            String errorMsg = createDescriptiveErrorMessage(e, server.getUrl(), protocol);
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

    /**
     * Creates a descriptive error message based on the exception type and context
     */
    private String createDescriptiveErrorMessage(Exception e, String url, String protocol) {
        String baseMessage = e.getMessage() != null ? e.getMessage() : "";
        
        if (e instanceof UnknownHostException) {
            try {
                URI uri = URI.create(url);
                String host = uri.getHost();
                if (host != null) {
                    return String.format("Host '%s' does not exist or cannot be resolved", host);
                }
            } catch (Exception ex) {
                // Fallback if URI parsing fails
            }
            return "Host does not exist or cannot be resolved: " + baseMessage;
        }
        
        if (e instanceof ConnectException) {
            // Check if the cause is UnresolvedAddressException (hostname not found)  
            Throwable cause = e.getCause();
            if (cause instanceof java.nio.channels.UnresolvedAddressException) {
                try {
                    URI uri = URI.create(url);
                    String host = uri.getHost();
                    if (host != null) {
                        return String.format("Host '%s' does not exist or cannot be resolved", host);
                    }
                } catch (Exception ex) {
                    // Fallback
                }
                return "Host does not exist or cannot be resolved";
            }
            
            // Check for null message which usually indicates hostname resolution issues
            if (baseMessage.isEmpty() && cause != null) {
                // For ConnectException with null message, this usually means hostname resolution failed
                try {
                    URI uri = URI.create(url);
                    String host = uri.getHost();
                    if (host != null) {
                        return String.format("Host '%s' does not exist or cannot be resolved", host);
                    }
                } catch (Exception ex) {
                    // Fallback
                }
                return "Host does not exist or cannot be resolved";
            }
            
            if (baseMessage.toLowerCase().contains("connection refused")) {
                try {
                    URI uri = URI.create(url);
                    String host = uri.getHost();
                    int port = uri.getPort();
                    if (port == -1) {
                        port = "https".equals(protocol) ? 443 : 80;
                    }
                    if (host != null) {
                        return String.format("Connection refused to host '%s' on port %d - port may not be open or service may not be running", 
                                host, port);
                    }
                } catch (Exception ex) {
                    // Fallback if URI parsing fails
                }
                return "Connection refused - port may not be open or service may not be running";
            }
            return "Connection failed: " + baseMessage;
        }
        
        if (e instanceof SocketTimeoutException) {
            return "Connection timeout - the server did not respond within the expected time";
        }
        
        if (e instanceof java.net.http.HttpTimeoutException) {
            return "HTTP request timeout - the server did not respond within the expected time";
        }
        
        if (e instanceof java.net.http.HttpConnectTimeoutException) {
            return "HTTP connection timeout - unable to establish connection within the expected time";
        }
        
        if (e instanceof IOException && baseMessage.toLowerCase().contains("no route to host")) {
            return "No route to host - network may be unreachable";
        }
        
        if (e instanceof IllegalArgumentException && (baseMessage.toLowerCase().contains("uri") || 
            baseMessage.toLowerCase().contains("url") || baseMessage.toLowerCase().contains("malformed"))) {
            return "Invalid URL format: " + url;
        }
        
        // Check for specific network-related IOException messages
        if (e instanceof IOException) {
            String lowerMessage = baseMessage.toLowerCase();
            if (lowerMessage.contains("name resolution failed") || 
                lowerMessage.contains("nodename nor servname") ||
                lowerMessage.contains("no address associated") ||
                lowerMessage.contains("name or service not known") ||
                lowerMessage.contains("host not found") ||
                lowerMessage.contains("unknown host")) {
                try {
                    URI uri = URI.create(url);
                    String host = uri.getHost();
                    if (host != null) {
                        return String.format("Host '%s' does not exist or cannot be resolved", host);
                    }
                } catch (Exception ex) {
                    // Fallback
                }
                return "Host does not exist or cannot be resolved";
            }
        }
        
        // Handle HttpConnectTimeoutException specifically
        if (e.getCause() instanceof UnknownHostException) {
            try {
                URI uri = URI.create(url);
                String host = uri.getHost();
                if (host != null) {
                    return String.format("Host '%s' does not exist or cannot be resolved", host);
                }
            } catch (Exception ex) {
                // Fallback
            }
            return "Host does not exist or cannot be resolved";
        }
        
        // Default fallback for other exceptions
        String exceptionType = e.getClass().getSimpleName();
        if (baseMessage.isEmpty()) {
            return exceptionType + ": Connection failed";
        }
        return exceptionType + ": " + baseMessage;
    }

    /**
     * Checks if the server response indicates MCP protocol support
     */
    private boolean isMcpCompliantResponse(HttpResponse<String> response) {
        if (response == null) return false;
        
        // Check response headers for MCP indicators
        String contentType = response.headers().firstValue("content-type").orElse("");
        if (contentType.contains("application/json")) {
            String body = response.body();
            if (body != null) {
                // Check for JSON-RPC structure which is typical for MCP
                return body.contains("jsonrpc") || body.contains("\"id\"") || 
                       body.contains("\"method\"") || body.contains("\"result\"") ||
                       body.contains("tools") || body.contains("resources");
            }
        }
        
        // Check for specific MCP-related headers
        return response.headers().map().keySet().stream()
                .anyMatch(header -> header.toLowerCase().contains("mcp") || 
                         header.toLowerCase().contains("json-rpc"));
    }

    /**
     * Creates an error message for HTTP status codes with MCP context
     */
    private String createHttpStatusErrorMessage(int statusCode, String endpoint, boolean mcpCompliant) {
        String baseMessage = String.format("HTTP %d from %s", statusCode, endpoint);
        
        switch (statusCode) {
            case 404:
                if (!mcpCompliant) {
                    return baseMessage + " - endpoint not found, server may not implement MCP protocol";
                }
                return baseMessage + " - endpoint not found";
            case 405:
                if (!mcpCompliant) {
                    return baseMessage + " - method not allowed, server may not implement MCP protocol correctly";
                }
                return baseMessage + " - method not allowed";
            case 500:
                return baseMessage + " - internal server error";
            case 502:
                return baseMessage + " - bad gateway, server may be down";
            case 503:
                return baseMessage + " - service unavailable, server may be overloaded";
            case 504:
                return baseMessage + " - gateway timeout";
            default:
                if (statusCode >= 400 && statusCode < 500) {
                    if (!mcpCompliant) {
                        return baseMessage + " - client error, server may not implement MCP protocol";
                    }
                    return baseMessage + " - client error";
                } else if (statusCode >= 500) {
                    return baseMessage + " - server error";
                }
                return baseMessage;
        }
    }


    private McpServer connectStdio(McpServer server) {
        log.info("Connecting via stdio to: {}", server.getName());
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
                log.info("Reusing existing stdio process for {}", server.getName());
            }

            server.setStatus("CONNECTED");
            server.setLastConnected(System.currentTimeMillis());
            server.setLastError(null);
            log.info("Stdio connection established with {}", server.getName());
        } catch (Exception e) {
            String errorMsg = createStdioErrorMessage(e, server.getUrl());
            log.error("Error connecting stdio to {}: {}", server.getName(), errorMsg, e);
            server.setStatus("ERROR");
            server.setLastConnected(System.currentTimeMillis());
            server.setLastError(errorMsg);
            // Only throw runtime exception for unexpected errors, not validation errors
            if (!(e instanceof IOException && (
                    e.getMessage().toLowerCase().contains("cannot run program") ||
                    e.getMessage().toLowerCase().contains("no such file")))) {
                throw new RuntimeException("Stdio connection failure: " + errorMsg);
            }
        }
        return server;
    }

    /**
     * Creates a descriptive error message for stdio connection failures
     */
    private String createStdioErrorMessage(Exception e, String url) {
        String command = url.substring("stdio://".length()).trim();
        
        if (e instanceof IOException) {
            String message = e.getMessage() != null ? e.getMessage() : "";
            if (message.toLowerCase().contains("cannot run program")) {
                return String.format("Command not found or not executable: '%s'", command);
            } else if (message.toLowerCase().contains("no such file")) {
                return String.format("Command not found: '%s'", command);
            } else if (message.toLowerCase().contains("permission denied")) {
                return String.format("Permission denied executing command: '%s'", command);
            }
            return String.format("Failed to execute stdio command '%s': %s", command, message);
        }
        
        if (e instanceof SecurityException) {
            return String.format("Security error executing command '%s': %s", command, e.getMessage());
        }
        
        return String.format("Stdio connection error with command '%s': %s", command, 
                e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName());
    }

    private void startStdioProcess(McpServer server) throws IOException {
        String command = server.getUrl().substring("stdio://".length()).trim();
        if (command.isEmpty()) {
            throw new RuntimeException("Empty stdio command");
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
            log.info("First stdio output: {}", firstLine);
        } else {
            log.debug("STDIO server started without initial output");
        }
    }

    private McpServer connectHttp(McpServer server) {
        log.info("Attempting HTTP connection to: {}", server.getUrl());

        try {
            // Attempt /mcp/health first, then the root and other endpoints if it fails
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
            } else if (response.statusCode() == 404 || response.statusCode() == 405 || response.statusCode() == 400) {
                // If /mcp/health does not exist or does not allow GET, try the root
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
                } else if (rootResponse.statusCode() == 404 || rootResponse.statusCode() == 405) {
                    // If the root also does not exist or does not allow GET, try a functional endpoint
                    log.info("Root endpoint returned {} , trying tools list endpoint for {}", rootResponse.statusCode(), server.getUrl());
                    String toolsUrl = server.getUrl() + "/mcp/tools/list";
                    String body = String.format("{\"jsonrpc\":\"2.0\",\"id\":\"%s\",\"method\":\"tools/list\",\"params\":{}}", UUID.randomUUID());
                    HttpRequest toolsRequest = HttpRequest.newBuilder()
                            .uri(URI.create(toolsUrl))
                            .timeout(Duration.ofSeconds(5))
                            .header("Content-Type", "application/json")
                            .POST(HttpRequest.BodyPublishers.ofString(body))
                            .build();

                    HttpResponse<String> toolsResponse = httpClient.send(toolsRequest, HttpResponse.BodyHandlers.ofString());

                    if (toolsResponse.statusCode() >= 200 && toolsResponse.statusCode() < 300) {
                        log.info("HTTP connection successful to tools endpoint {}", toolsUrl);
                        server.setStatus("CONNECTED");
                        server.setLastError(null);
                    } else {
                        boolean mcpCompliant = isMcpCompliantResponse(toolsResponse);
                        String errorMsg = createHttpStatusErrorMessage(toolsResponse.statusCode(), "tools endpoint", mcpCompliant);
                        log.error("HTTP connection returned status {} for {}", toolsResponse.statusCode(), toolsUrl);
                        server.setStatus("ERROR");
                        server.setLastError(errorMsg);
                    }
                } else {
                    boolean mcpCompliant = isMcpCompliantResponse(rootResponse);
                    String errorMsg = createHttpStatusErrorMessage(rootResponse.statusCode(), "root endpoint", mcpCompliant);
                    log.warn("HTTP connection returned status {} for {}", rootResponse.statusCode(), server.getUrl());
                    server.setStatus("ERROR");
                    server.setLastError(errorMsg);
                }
            } else {
                boolean mcpCompliant = isMcpCompliantResponse(response);
                String errorMsg = createHttpStatusErrorMessage(response.statusCode(), "health endpoint", mcpCompliant);
                log.warn("HTTP connection returned status {} for {}", response.statusCode(), server.getUrl());
                server.setStatus("ERROR");
                server.setLastError(errorMsg);
            }
        } catch (Exception e) {
            String errorMsg = createDescriptiveErrorMessage(e, server.getUrl(), "http");
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

            if (host == null) {
                String errorMsg = "Invalid WebSocket URL: missing host in " + server.getUrl();
                log.error("WebSocket connection error to {}: {}", server.getName(), errorMsg);
                server.setStatus("ERROR");
                server.setLastError(errorMsg);
                server.setLastConnected(System.currentTimeMillis());
                return server;
            }

            if (port == -1) {
                port = uri.getScheme().equals("wss") ? 443 : 80;
            }

            try (Socket socket = new Socket()) {
                socket.connect(new InetSocketAddress(host, port), 5000);
                log.info("WebSocket TCP connection successful to {}:{}", host, port);
                server.setStatus("CONNECTED");
                server.setLastError(null);
            }
        } catch (Exception e) {
            String errorMsg = createDescriptiveErrorMessage(e, server.getUrl(), "websocket");
            log.error("WebSocket connection error to {}: {}", server.getName(), errorMsg);
            server.setStatus("ERROR");
            server.setLastError(errorMsg);
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

            if (host == null) {
                String errorMsg = "Invalid TCP URL: missing host in " + server.getUrl();
                log.error("TCP connection error to {}: {}", server.getName(), errorMsg);
                server.setStatus("ERROR");
                server.setLastError(errorMsg);
                server.setLastConnected(System.currentTimeMillis());
                return server;
            }

            if (port == -1) {
                String errorMsg = "Invalid TCP URL: missing port in " + server.getUrl();
                log.error("TCP connection error to {}: {}", server.getName(), errorMsg);
                server.setStatus("ERROR");
                server.setLastError(errorMsg);
                server.setLastConnected(System.currentTimeMillis());
                return server;
            }

            try (Socket socket = new Socket()) {
                socket.connect(new InetSocketAddress(host, port), 5000);
                log.info("TCP connection successful to {}:{}", host, port);
                server.setStatus("CONNECTED");
                server.setLastError(null);
            }
        } catch (Exception e) {
            String errorMsg = createDescriptiveErrorMessage(e, server.getUrl(), "tcp");
            log.error("TCP connection error to {}: {}", server.getName(), errorMsg);
            server.setStatus("ERROR");
            server.setLastError(errorMsg);
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
    // Add these public methods in McpServerService.java

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