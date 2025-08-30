package org.shark.mentor.mcp.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.shark.mentor.mcp.model.McpCapabilities;
import org.shark.mentor.mcp.model.McpServer;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Test for enhanced MCP client features
 */
@ExtendWith(MockitoExtension.class)
class McpEnhancedFeaturesTest {

    @Mock
    private McpServerService mcpServerService;

    private McpResourceService mcpResourceService;
    private McpPromptService mcpPromptService;
    private McpCapabilityService mcpCapabilityService;
    private McpLoggingService mcpLoggingService;
    private McpNotificationService mcpNotificationService;

    @BeforeEach
    void setUp() {
        mcpResourceService = new McpResourceService(mcpServerService);
        mcpPromptService = new McpPromptService(mcpServerService);
        mcpCapabilityService = new McpCapabilityService();
        mcpLoggingService = new McpLoggingService();
        mcpNotificationService = new McpNotificationService();
    }

    @Test
    void testCapabilitiesModel() {
        McpCapabilities capabilities = McpCapabilities.builder()
                .tools(true)
                .resources(true)
                .prompts(true)
                .logging(true)
                .sampling(false)
                .build();

        assertNotNull(capabilities);
        assertTrue(capabilities.getTools());
        assertTrue(capabilities.getResources());
        assertTrue(capabilities.getPrompts());
        assertTrue(capabilities.getLogging());
        assertFalse(capabilities.getSampling());
    }

    @Test
    void testLoggingService() {
        String serverId = "test-server";
        String message = "Test log message";

        mcpLoggingService.logInfo(serverId, message);
        
        var logs = mcpLoggingService.getLogs(serverId);
        assertFalse(logs.isEmpty());
        assertEquals(1, logs.size());
        assertEquals("info", logs.get(0).getLevel());
        assertEquals(message, logs.get(0).getMessage());
        assertEquals(serverId, logs.get(0).getServerId());
    }

    @Test
    void testNotificationService() {
        String serverId = "test-server";
        String method = "resources/updated";
        Object params = "test-params";

        mcpNotificationService.handleNotification(serverId, method, params);
        
        var notifications = mcpNotificationService.getNotifications(serverId);
        assertFalse(notifications.isEmpty());
        assertEquals(1, notifications.size());
        assertEquals(method, notifications.get(0).getMethod());
        assertEquals(params, notifications.get(0).getParams());
        assertEquals(serverId, notifications.get(0).getServerId());
    }

    @Test
    void testResourceServiceWithMockServer() {
        McpServer server = McpServer.builder()
                .id("test-server")
                .name("Test Server")
                .url("http://localhost:3000")
                .status("CONNECTED")
                .build();

        var resources = mcpResourceService.getResources(server);
        assertNotNull(resources);
        // Since we don't have a real server, this should return empty list
        assertTrue(resources.isEmpty());
    }

    @Test
    void testPromptServiceWithMockServer() {
        McpServer server = McpServer.builder()
                .id("test-server")
                .name("Test Server")
                .url("http://localhost:3000")
                .status("CONNECTED")
                .build();

        var prompts = mcpPromptService.getPrompts(server);
        assertNotNull(prompts);
        // Since we don't have a real server, this should return empty list
        assertTrue(prompts.isEmpty());
    }

    @Test
    void testCapabilityServiceWithMockServer() {
        McpServer server = McpServer.builder()
                .id("test-server")
                .name("Test Server")
                .url("http://localhost:3000")
                .status("CONNECTED")
                .build();

        var capabilities = mcpCapabilityService.discoverCapabilities(server);
        assertNotNull(capabilities);
        // Should return default capabilities when connection fails
        assertTrue(capabilities.getTools());
        assertFalse(capabilities.getResources());
        assertFalse(capabilities.getPrompts());
    }

    @Test
    void testServerWithCapabilities() {
        McpCapabilities capabilities = McpCapabilities.builder()
                .tools(true)
                .resources(true)
                .prompts(true)
                .build();

        McpServer server = McpServer.builder()
                .id("test-server")
                .name("Test Server")
                .url("http://localhost:3000")
                .status("CONNECTED")
                .capabilities(capabilities)
                .build();

        assertNotNull(server.getCapabilities());
        assertTrue(server.getCapabilities().getTools());
        assertTrue(server.getCapabilities().getResources());
        assertTrue(server.getCapabilities().getPrompts());
    }
}