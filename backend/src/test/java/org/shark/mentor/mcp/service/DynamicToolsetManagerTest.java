package org.shark.mentor.mcp.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.shark.mentor.mcp.application.service.tool.DynamicToolsetManager;
import org.shark.mentor.mcp.application.service.tool.McpToolService;
import org.shark.mentor.mcp.domain.model.McpServer;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DynamicToolsetManagerTest {

    @Mock
    private McpToolService mcpToolService;

    private DynamicToolsetManager dynamicToolsetManager;
    private McpServer testServer;

    @BeforeEach
    void setUp() {
        dynamicToolsetManager = new DynamicToolsetManager(mcpToolService);
        testServer = new McpServer("github-server", "GitHub MCP Server", "GitHub integration server", "http://localhost:8080", "active");
    }

    @Test
    void shouldDetectCapabilityForBranchIntent() {
        // Given
        List<Map<String, Object>> tools = List.of(
            Map.of("name", "list_branches", "description", "List all branches in a repository")
        );
        String userMessage = "show me all branches";

        // When
        boolean hasCapability = dynamicToolsetManager.hasCapabilityForIntent(tools, userMessage);

        // Then
        assertTrue(hasCapability);
    }

    @Test
    void shouldDetectMissingCapabilityForBranchIntent() {
        // Given
        List<Map<String, Object>> tools = List.of(
            Map.of("name", "list_repositories", "description", "List all repositories")
        );
        String userMessage = "dame todos los branches";

        // When
        boolean hasCapability = dynamicToolsetManager.hasCapabilityForIntent(tools, userMessage);

        // Then
        assertFalse(hasCapability);
    }

    @Test
    void shouldDiscoverToolsetsWhenAvailable() throws Exception {
        // Given
        List<Map<String, Object>> availableTools = List.of(
            Map.of("name", "list_available_toolsets", "description", "List available toolsets"),
            Map.of("name", "other_tool", "description", "Some other tool")
        );
        
        when(mcpToolService.getTools(testServer)).thenReturn(availableTools);
        when(mcpToolService.callToolViaHttp(eq(testServer), eq("list_available_toolsets"), any()))
            .thenReturn("{\"result\":{\"toolsets\":[{\"name\":\"github\",\"enabled\":false}]}}");

        // When
        List<Map<String, Object>> toolsets = dynamicToolsetManager.discoverAvailableToolsets(testServer);

        // Then
        assertFalse(toolsets.isEmpty());
        verify(mcpToolService).callToolViaHttp(eq(testServer), eq("list_available_toolsets"), any());
    }

    @Test
    void shouldReturnEmptyListWhenNoToolsetsAvailable() {
        // Given
        List<Map<String, Object>> availableTools = List.of(
            Map.of("name", "regular_tool", "description", "A regular tool")
        );
        
        when(mcpToolService.getTools(testServer)).thenReturn(availableTools);

        // When
        List<Map<String, Object>> toolsets = dynamicToolsetManager.discoverAvailableToolsets(testServer);

        // Then
        assertTrue(toolsets.isEmpty());
    }

    @Test
    void shouldEnableToolsetSuccessfully() throws Exception {
        // Given
        List<Map<String, Object>> availableTools = List.of(
            Map.of("name", "enable_toolset", "description", "Enable a toolset")
        );
        
        when(mcpToolService.getTools(testServer)).thenReturn(availableTools);
        when(mcpToolService.callToolViaHttp(eq(testServer), eq("enable_toolset"), any()))
            .thenReturn("{\"result\":{\"success\":true}}");

        // When
        boolean result = dynamicToolsetManager.enableToolset(testServer, "github");

        // Then
        assertTrue(result);
        verify(mcpToolService).callToolViaHttp(eq(testServer), eq("enable_toolset"), 
            argThat(params -> "github".equals(((Map<String, Object>) params).get("toolset"))));
    }

    @Test
    void shouldFailToEnableToolsetWhenNoEnableToolAvailable() {
        // Given
        List<Map<String, Object>> availableTools = List.of(
            Map.of("name", "regular_tool", "description", "A regular tool")
        );
        
        when(mcpToolService.getTools(testServer)).thenReturn(availableTools);

        // When
        boolean result = dynamicToolsetManager.enableToolset(testServer, "github");

        // Then
        assertFalse(result);
    }

    @Test
    void shouldCacheToolsetsDiscovery() throws Exception {
        // Given
        List<Map<String, Object>> availableTools = List.of(
            Map.of("name", "list_available_toolsets", "description", "List available toolsets")
        );
        
        when(mcpToolService.getTools(testServer)).thenReturn(availableTools);
        when(mcpToolService.callToolViaHttp(eq(testServer), eq("list_available_toolsets"), any()))
            .thenReturn("{\"result\":{\"toolsets\":[{\"name\":\"github\",\"enabled\":false}]}}");

        // When - call twice
        dynamicToolsetManager.discoverAvailableToolsets(testServer);
        List<Map<String, Object>> secondCall = dynamicToolsetManager.discoverAvailableToolsets(testServer);

        // Then - should only call the service once due to caching
        verify(mcpToolService, times(1)).callToolViaHttp(any(), any(), any());
        assertFalse(secondCall.isEmpty());
    }

    @Test
    void shouldInvalidateCacheCorrectly() throws Exception {
        // Given
        List<Map<String, Object>> availableTools = List.of(
            Map.of("name", "list_available_toolsets", "description", "List available toolsets")
        );
        
        when(mcpToolService.getTools(testServer)).thenReturn(availableTools);
        when(mcpToolService.callToolViaHttp(eq(testServer), eq("list_available_toolsets"), any()))
            .thenReturn("{\"result\":{\"toolsets\":[{\"name\":\"github\",\"enabled\":false}]}}");

        // When
        dynamicToolsetManager.discoverAvailableToolsets(testServer); // First call
        dynamicToolsetManager.invalidateToolCache(testServer); // Invalidate cache
        dynamicToolsetManager.discoverAvailableToolsets(testServer); // Second call after invalidation

        // Then - should call the service twice since cache was invalidated
        verify(mcpToolService, times(2)).callToolViaHttp(any(), any(), any());
    }

    @Test
    void shouldHandleSpanishBranchRequest() {
        // Given
        List<Map<String, Object>> tools = List.of(
            Map.of("name", "list_branches", "description", "List repository branches")
        );
        String userMessage = "muéstrame todas las ramas";

        // When
        boolean hasCapability = dynamicToolsetManager.hasCapabilityForIntent(tools, userMessage);

        // Then
        assertTrue(hasCapability);
    }

    @Test
    void shouldHandleRepositoryRelatedIntents() {
        // Given
        List<Map<String, Object>> tools = List.of(
            Map.of("name", "list_repositories", "description", "List user repositories")
        );
        String userMessage = "show me all repositories";

        // When
        boolean hasCapability = dynamicToolsetManager.hasCapabilityForIntent(tools, userMessage);

        // Then
        assertTrue(hasCapability);
    }
}