package org.shark.mentor.mcp.service;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.shark.mentor.mcp.config.McpProperties;
import org.shark.mentor.mcp.model.McpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class for validating descriptive error messages in McpServerService
 */
class McpServerServiceErrorMessageTest {

    private McpServerService service;
    private HttpServer httpServer;

    @BeforeEach
    void setUp() {
        McpProperties properties = new McpProperties();
        service = new McpServerService(properties);
    }

    @AfterEach
    void tearDown() {
        if (httpServer != null) {
            httpServer.stop(0);
        }
    }

    @Test
    void connectHttp_UnknownHost_ReturnsDescriptiveError() {
        // Use a more clearly invalid hostname
        McpServer server = new McpServer("test", "Test", "", "http://this-host-definitely-does-not-exist.invalid", "DISCONNECTED");
        service.addServer(server);

        McpServer result = service.connectToServer("test");

        assertEquals("ERROR", result.getStatus());
        assertNotNull(result.getLastError());
        // Check for various possible error indicators
        String error = result.getLastError().toLowerCase();
        boolean hasHostError = error.contains("host") && 
                               (error.contains("does not exist") || 
                                error.contains("cannot be resolved") || 
                                error.contains("name resolution") ||
                                error.contains("unknown"));
        assertTrue(hasHostError, 
                "Error message should indicate host doesn't exist. Actual: " + result.getLastError());
    }

    @Test
    void connectTcp_ConnectionRefused_ReturnsDescriptiveError() {
        // Find an available port and then don't use it to simulate connection refused
        int unusedPort = findUnusedPort();
        
        McpServer server = new McpServer("test", "Test", "", "tcp://localhost:" + unusedPort, "DISCONNECTED");
        service.addServer(server);

        McpServer result = service.connectToServer("test");

        assertEquals("ERROR", result.getStatus());
        assertNotNull(result.getLastError());
        assertTrue(result.getLastError().contains("Connection refused") && 
                   result.getLastError().contains("port " + unusedPort),
                "Error message should indicate connection refused and port. Actual: " + result.getLastError());
    }

    @Test
    void connectHttp_InvalidUrl_ReturnsDescriptiveError() {
        McpServer server = new McpServer("test", "Test", "", "invalid-url", "DISCONNECTED");
        service.addServer(server);

        McpServer result = service.connectToServer("test");

        assertEquals("ERROR", result.getStatus());
        assertNotNull(result.getLastError());
        assertTrue(result.getLastError().contains("Invalid URL format") || 
                   result.getLastError().contains("IllegalArgumentException"),
                "Error message should indicate invalid URL. Actual: " + result.getLastError());
    }

    @Test
    void connectHttp_ServerReturns404_IndicatesMcpNonCompliance() throws IOException {
        setupHttpServerWith404();
        int port = httpServer.getAddress().getPort();
        
        McpServer server = new McpServer("test", "Test", "", "http://localhost:" + port, "DISCONNECTED");
        service.addServer(server);

        McpServer result = service.connectToServer("test");

        assertEquals("ERROR", result.getStatus());
        assertNotNull(result.getLastError());
        assertTrue(result.getLastError().contains("404") && 
                   result.getLastError().toLowerCase().contains("mcp protocol"),
                "Error message should indicate 404 and MCP protocol issue. Actual: " + result.getLastError());
    }

    @Test
    void connectHttp_ServerReturns200WithMcpResponse_Succeeds() throws IOException {
        setupHttpServerWithMcpResponse();
        int port = httpServer.getAddress().getPort();
        
        McpServer server = new McpServer("test", "Test", "", "http://localhost:" + port, "DISCONNECTED");
        service.addServer(server);

        McpServer result = service.connectToServer("test");

        assertEquals("CONNECTED", result.getStatus());
        assertNull(result.getLastError());
    }

    @Test
    void connectTcp_MissingHost_ReturnsDescriptiveError() {
        McpServer server = new McpServer("test", "Test", "", "tcp://:8080", "DISCONNECTED");
        service.addServer(server);

        McpServer result = service.connectToServer("test");

        assertEquals("ERROR", result.getStatus());
        assertNotNull(result.getLastError());
        assertTrue(result.getLastError().contains("missing host"),
                "Error message should indicate missing host. Actual: " + result.getLastError());
    }

    @Test
    void connectTcp_MissingPort_ReturnsDescriptiveError() {
        McpServer server = new McpServer("test", "Test", "", "tcp://localhost", "DISCONNECTED");
        service.addServer(server);

        McpServer result = service.connectToServer("test");

        assertEquals("ERROR", result.getStatus());
        assertNotNull(result.getLastError());
        assertTrue(result.getLastError().contains("missing port"),
                "Error message should indicate missing port. Actual: " + result.getLastError());
    }

    @Test
    void connectWebSocket_MissingHost_ReturnsDescriptiveError() {
        McpServer server = new McpServer("test", "Test", "", "ws://:8080", "DISCONNECTED");
        service.addServer(server);

        McpServer result = service.connectToServer("test");

        assertEquals("ERROR", result.getStatus());
        assertNotNull(result.getLastError());
        assertTrue(result.getLastError().contains("missing host"),
                "Error message should indicate missing host. Actual: " + result.getLastError());
    }

    @Test
    void connectStdio_InvalidCommand_ReturnsDescriptiveError() {
        McpServer server = new McpServer("test", "Test", "", "stdio://nonexistent-command-12345", "DISCONNECTED");
        service.addServer(server);

        McpServer result = service.connectToServer("test");
        
        assertEquals("ERROR", result.getStatus());
        assertNotNull(result.getLastError());
        assertTrue(result.getLastError().contains("Command not found") || 
                   result.getLastError().contains("cannot run program"),
                "Error message should indicate command not found. Actual: " + result.getLastError());
    }

    private int findUnusedPort() {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        } catch (IOException e) {
            return 9999; // fallback
        }
    }

    private void setupHttpServerWith404() throws IOException {
        httpServer = HttpServer.create(new InetSocketAddress(0), 0);
        httpServer.createContext("/", exchange -> {
            exchange.sendResponseHeaders(404, -1);
            exchange.close();
        });
        httpServer.start();
    }

    private void setupHttpServerWithMcpResponse() throws IOException {
        httpServer = HttpServer.create(new InetSocketAddress(0), 0);
        httpServer.createContext("/mcp/health", exchange -> {
            byte[] response = "{\"status\":\"ok\"}".getBytes();
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(response);
            }
            exchange.close();
        });
        httpServer.start();
    }
}