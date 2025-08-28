package org.shark.mentor.mcp.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.shark.mentor.mcp.config.McpProperties;
import org.shark.mentor.mcp.model.McpServer;
import org.shark.mentor.mcp.websocket.McpConfigWebSocketHandler;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Demonstration test showing improved error messages for various connection scenarios
 */
class McpServerServiceErrorMessageDemoTest {

    private McpServerService service;

    @BeforeEach
    void setUp() {
        McpProperties properties = new McpProperties();
        McpConfigWebSocketHandler mockWebSocketHandler = new McpConfigWebSocketHandler();
        service = new McpServerService(properties, mockWebSocketHandler);
    }

    @Test
    void demonstrateImprovedErrorMessages() {
        // Test 1: Unknown host
        McpServer unknownHost = new McpServer("unknown", "Unknown Host", "", 
                "http://this-host-does-not-exist.invalid", "DISCONNECTED");
        service.addServer(unknownHost);
        McpServer result1 = service.connectToServer("unknown");
        
        System.out.println("=== Unknown Host Error ===");
        System.out.println("Old message would be: 'Connection failed:'");
        System.out.println("New message: '" + result1.getLastError() + "'");
        System.out.println();
        
        // Test 2: Invalid command for stdio
        McpServer invalidCmd = new McpServer("invalidcmd", "Invalid Command", "", 
                "stdio://nonexistent-command", "DISCONNECTED");
        service.addServer(invalidCmd);
        McpServer result2 = service.connectToServer("invalidcmd");
        
        System.out.println("=== Invalid Stdio Command Error ===");
        System.out.println("Old message would be: 'IOException: Cannot run program...'");
        System.out.println("New message: '" + result2.getLastError() + "'");
        System.out.println();
        
        // Test 3: Invalid URL format
        McpServer invalidUrl = new McpServer("invalidurl", "Invalid URL", "", 
                "not-a-valid-url", "DISCONNECTED");
        service.addServer(invalidUrl);
        McpServer result3 = service.connectToServer("invalidurl");
        
        System.out.println("=== Invalid URL Format Error ===");
        System.out.println("Old message would be: 'Unsupported protocol: unknown'");
        System.out.println("New message: '" + result3.getLastError() + "'");
        System.out.println();
        
        // Test 4: Missing host in TCP URL
        McpServer missingHost = new McpServer("missinghost", "Missing Host", "", 
                "tcp://:8080", "DISCONNECTED");
        service.addServer(missingHost);
        McpServer result4 = service.connectToServer("missinghost");
        
        System.out.println("=== Missing Host in TCP URL Error ===");
        System.out.println("Old message would be: 'NullPointerException' or similar");
        System.out.println("New message: '" + result4.getLastError() + "'");
        System.out.println();
        
        // Verify all errors are more descriptive
        assertTrue(result1.getLastError().contains("does not exist") || 
                   result1.getLastError().contains("cannot be resolved"));
        assertTrue(result2.getLastError().contains("Command not found"));
        assertTrue(result3.getLastError().contains("Invalid URL format"));
        assertTrue(result4.getLastError().contains("missing host"));
    }
}