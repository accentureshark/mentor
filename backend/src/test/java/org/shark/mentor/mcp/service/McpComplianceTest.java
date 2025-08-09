package org.shark.mentor.mcp.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.shark.mentor.mcp.config.LlmProperties;
import org.shark.mentor.mcp.model.McpServer;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Integration test for the MCP Tool Orchestrator to verify MCP compliance
 */
@ExtendWith(MockitoExtension.class)
class McpComplianceTest {

    @Mock
    private McpServerService mcpServerService;

    @Mock
    private McpToolService mcpToolService;

    private McpToolOrchestrator orchestrator;
    private LlmProperties llmProperties;

    @BeforeEach
    void setUp() {
        orchestrator = new McpToolOrchestrator(mcpServerService, mcpToolService);

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

        // Simula que no hay tools disponibles
        when(mcpToolService.getTools(server)).thenReturn(Collections.emptyList());

        // When executing a tool
        String result = orchestrator.executeTool(server, "list repositories");

        // Then it should handle MCP protocol appropriately
        assertNotNull(result);
        // The orchestrator should gracefully handle when tools are not available
        assertTrue(result.contains("No hay herramientas disponibles") || result.contains("Error"));
    }

    @Test
    void testErrorHandling() {
        McpServer server = McpServer.builder()
                .id("test-server")
                .name("Test MCP Server")
                .url("http://localhost:3000")
                .status("CONNECTED")
                .build();

        // Simula que getTools lanza una excepción
        when(mcpToolService.getTools(server)).thenThrow(new RuntimeException("Simulated error"));

        String result = orchestrator.executeTool(server, "list repositories");

        // Then it should handle errors gracefully
        assertNotNull(result);
        assertTrue(result.contains("Error") || result.contains("No tools") || result.contains("No hay herramientas disponibles"));
    }
}