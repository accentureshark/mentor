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

import static org.junit.jupiter.api.Assertions.assertEquals;

class McpServerServiceHttpTest {

    private HttpServer httpServer;

    @BeforeEach
    void setUp() throws IOException {
        httpServer = HttpServer.create(new InetSocketAddress(0), 0);
        httpServer.createContext("/mcp/health", exchange -> {
            exchange.sendResponseHeaders(405, -1);
            exchange.close();
        });
        httpServer.createContext("/mcp/tools/list", exchange -> {
            byte[] resp = "{}".getBytes();
            exchange.sendResponseHeaders(200, resp.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(resp);
            }
        });
        httpServer.createContext("/", exchange -> {
            exchange.sendResponseHeaders(405, -1);
            exchange.close();
        });
        httpServer.start();
    }

    @AfterEach
    void tearDown() {
        httpServer.stop(0);
    }

    @Test
    void connectHttpTreats405AsNotFound() {
        int port = httpServer.getAddress().getPort();
        McpProperties properties = new McpProperties();
        McpServerService service = new McpServerService(properties);
        McpServer server = new McpServer("test", "Test", "", "http://localhost:" + port, "DISCONNECTED");
        service.addServer(server);

        McpServer result = service.connectToServer("test");

        assertEquals("CONNECTED", result.getStatus());
    }
}
