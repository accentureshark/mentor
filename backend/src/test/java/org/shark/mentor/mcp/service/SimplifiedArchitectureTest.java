package org.shark.mentor.mcp.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.shark.mentor.mcp.config.LlmProperties;
import org.shark.mentor.mcp.model.ChatMessage;
import org.shark.mentor.mcp.model.McpRequest;
import org.shark.mentor.mcp.model.McpServer;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Test for the simplified langchain4j-based architecture
 */
@ExtendWith(MockitoExtension.class)
class SimplifiedArchitectureTest {

    @Mock
    private McpServerService mcpServerService;
    
    @Mock
    private McpToolOrchestrator mcpToolOrchestrator;
    
    @Mock
    private LlmServiceEnhanced llmService;

    private ChatServiceSimplified chatService;

    @BeforeEach
    void setUp() {
        chatService = new ChatServiceSimplified(mcpServerService, mcpToolOrchestrator, llmService);
    }

    @Test
    void testSendMessage_ServerNotFound() {
        // Given
        McpRequest request = McpRequest.builder()
                .serverId("non-existent")
                .message("Test message")
                .conversationId("test-conv")
                .build();

        when(mcpServerService.getServer("non-existent")).thenReturn(Optional.empty());

        // When
        ChatMessage response = chatService.sendMessage(request);

        // Then
        assertNotNull(response);
        assertEquals("ASSISTANT", response.getRole());
        assertTrue(response.getContent().contains("Server not found"));
        assertEquals("non-existent", response.getServerId());
    }

    @Test
    void testSendMessage_ServerNotConnected() {
        // Given
        McpRequest request = McpRequest.builder()
                .serverId("test-server")
                .message("Test message")
                .conversationId("test-conv")
                .build();

        McpServer server = McpServer.builder()
                .id("test-server")
                .name("Test Server")
                .status("DISCONNECTED")
                .build();

        when(mcpServerService.getServer("test-server")).thenReturn(Optional.of(server));

        // When
        ChatMessage response = chatService.sendMessage(request);

        // Then
        assertNotNull(response);
        assertEquals("ASSISTANT", response.getRole());
        assertTrue(response.getContent().contains("Server is not connected"));
    }

    @Test
    void testSendMessage_Success() {
        // Given
        McpRequest request = McpRequest.builder()
                .serverId("test-server")
                .message("Search for repositories")
                .conversationId("test-conv")
                .build();

        McpServer server = McpServer.builder()
                .id("test-server")
                .name("Test Server")
                .status("CONNECTED")
                .build();

        when(mcpServerService.getServer("test-server")).thenReturn(Optional.of(server));
        when(mcpToolOrchestrator.executeTool(server, "Search for repositories"))
                .thenReturn("Found 5 repositories");
        when(llmService.generateWithMemory("test-conv", "Search for repositories", "Found 5 repositories"))
                .thenReturn("Based on the search results, I found 5 repositories that match your criteria.");

        // When
        ChatMessage response = chatService.sendMessage(request);

        // Then
        assertNotNull(response);
        assertEquals("ASSISTANT", response.getRole());
        assertEquals("Based on the search results, I found 5 repositories that match your criteria.", 
                response.getContent());
        assertEquals("test-server", response.getServerId());
        
        // Verify interactions
        verify(mcpToolOrchestrator).executeTool(server, "Search for repositories");
        verify(llmService).generateWithMemory("test-conv", "Search for repositories", "Found 5 repositories");
    }

    @Test
    void testConversationManagement() {
        // Given
        String conversationId = "test-conversation";

        // When - initially empty
        assertTrue(chatService.getConversation(conversationId).isEmpty());

        // Add a message through sendMessage
        McpRequest request = McpRequest.builder()
                .serverId("test-server")
                .message("Hello")
                .conversationId(conversationId)
                .build();

        McpServer server = McpServer.builder()
                .id("test-server")
                .name("Test Server")
                .status("CONNECTED")
                .build();

        when(mcpServerService.getServer("test-server")).thenReturn(Optional.of(server));
        when(mcpToolOrchestrator.executeTool(any(), any())).thenReturn("Test context");
        when(llmService.generateWithMemory(any(), any(), any())).thenReturn("Test response");

        chatService.sendMessage(request);

        // Then - should have messages
        assertEquals(2, chatService.getConversation(conversationId).size()); // User + Assistant

        // When - clear conversation
        chatService.clearConversation(conversationId);

        // Then - should be empty again
        assertTrue(chatService.getConversation(conversationId).isEmpty());
        verify(llmService).clearConversation(conversationId);
    }
}