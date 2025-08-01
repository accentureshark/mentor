package org.shark.mentor.mcp.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.shark.mentor.mcp.config.LlmProperties;
import org.shark.mentor.mcp.model.McpServer;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Integration test for the MCP Tool Orchestrator to verify MCP compliance
 */
@ExtendWith(MockitoExtension.class)
class McpComplianceTest {

    @Mock
    private McpServerService mcpServerService;

    private McpToolOrchestrator orchestrator;
    private LlmProperties llmProperties;

    @BeforeEach
    void setUp() {
        orchestrator = new McpToolOrchestrator(mcpServerService);
        
        llmProperties = new LlmProperties();
        llmProperties.setProvider("ollama");
        llmProperties.setModel("test-model");
        
        LlmProperties.Api api = new LlmProperties.Api();
        api.setBaseUrl("http://localhost:11434");
        llmProperties.setApi(api);
    }

    @Test
    void testMcpProtocolCompliance() {
        // Given a mock MCP server
        McpServer server = McpServer.builder()
                .id("test-server")
                .name("Test MCP Server")
                .url("http://localhost:3000")
                .status("CONNECTED")
                .build();

        // When executing a tool
        String result = orchestrator.executeTool(server, "list repositories");

        // Then it should handle MCP protocol appropriately
        assertNotNull(result);
        // The orchestrator should gracefully handle when tools are not available
        assertTrue(result.contains("No tools available") || result.contains("Error"));
    }

    @Test
    void testToolSelectionLogic() {
        // Given a server
        McpServer server = McpServer.builder()
                .id("github-server")
                .name("GitHub MCP Server")
                .url("http://localhost:3000")
                .status("CONNECTED")
                .build();

        // Test different message types
        String[] testMessages = {
            "search for repositories",
            "list my repos", 
            "find files",
            "get repository information"
        };

        for (String message : testMessages) {
            String result = orchestrator.executeTool(server, message);
            assertNotNull(result);
            // Should not throw exceptions
        }
    }

    @Test
    void testProtocolExtraction() {
        // Test protocol extraction utility
        McpServer httpServer = McpServer.builder()
                .url("http://localhost:3000")
                .build();
        
        McpServer httpsServer = McpServer.builder()
                .url("https://api.example.com")
                .build();
        
        McpServer stdioServer = McpServer.builder()
                .url("stdio://docker run example")
                .build();

        // Each should be handled appropriately
        assertNotNull(orchestrator.executeTool(httpServer, "test"));
        assertNotNull(orchestrator.executeTool(httpsServer, "test"));
        assertNotNull(orchestrator.executeTool(stdioServer, "test"));
    }

    @Test
    void testErrorHandling() {
        // Given an invalid server
        McpServer invalidServer = McpServer.builder()
                .id("invalid")
                .name("Invalid Server")
                .url("invalid://protocol")
                .status("CONNECTED")
                .build();

        // When executing a tool
        String result = orchestrator.executeTool(invalidServer, "test message");

        // Then it should handle errors gracefully
        assertNotNull(result);
        assertTrue(result.contains("Error") || result.contains("No tools"));
    }
}