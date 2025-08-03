package org.shark.mentor.mcp.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.shark.mentor.mcp.model.ChatMessage;
import org.shark.mentor.mcp.model.McpRequest;
import org.shark.mentor.mcp.model.McpServer;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Integration test for multilingual chat functionality
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MultilingualChatIntegrationTest {

    @Mock
    private McpServerService mcpServerService;
    
    @Mock
    private LlmService llmService;
    
    @Mock
    private McpToolService mcpToolService;
    
    @Mock
    private McpToolOrchestrator mcpToolOrchestrator;
    
    @Mock
    private LlmServiceEnhanced llmServiceEnhanced;
    
    private TranslationService translationService;
    private ChatService chatService;

    @BeforeEach
    void setUp() {
        translationService = new TranslationService(llmServiceEnhanced);
        chatService = new ChatService(
            mcpServerService,
            llmService,
            Optional.of(mcpToolOrchestrator),
            Optional.of(llmServiceEnhanced),
            mcpToolService,
            translationService
        );
    }

    @Test
    void testMultilingualChat_SpanishQuestion() {
        // Given
        String spanishQuestion = "¿Qué películas de acción están disponibles?";
        String englishTranslation = "What action movies are available?";
        String englishContext = "Movies found: [Terminator, Die Hard, Speed]";
        String spanishContext = "Películas encontradas: [El Terminador, Duro de Matar, Máxima Velocidad]";
        String finalResponse = "Encontré estas películas de acción: El Terminador, Duro de Matar, y Máxima Velocidad.";
        
        McpRequest request = McpRequest.builder()
                .serverId("test-server")
                .message(spanishQuestion)
                .conversationId("test-conv")
                .build();

        McpServer server = McpServer.builder()
                .id("test-server")
                .name("Test Movie Server")
                .status("CONNECTED")
                .build();

        // Mock server and tool responses
        when(mcpServerService.getServer("test-server")).thenReturn(Optional.of(server));
        
        // Mock translation
        when(llmServiceEnhanced.generateWithMemory(eq("translation_en"), anyString(), eq("")))
                .thenReturn(englishTranslation);
        
        // Mock MCP tool execution for both languages
        when(mcpToolOrchestrator.executeTool(server, englishTranslation))
                .thenReturn(englishContext);
        when(mcpToolOrchestrator.executeTool(server, spanishQuestion))
                .thenReturn(spanishContext);
        
        // Mock LLM response generation
        when(llmServiceEnhanced.generateWithMemory(eq("test-conv"), eq(spanishQuestion), anyString()))
                .thenReturn(finalResponse);

        // When
        ChatMessage response = chatService.sendMessage(request);

        // Then
        assertNotNull(response);
        assertEquals("ASSISTANT", response.getRole());
        assertEquals(finalResponse, response.getContent());
        assertEquals("test-server", response.getServerId());
        
        // Verify multilingual interaction
        verify(llmServiceEnhanced).generateWithMemory(eq("translation_en"), anyString(), eq(""));
        verify(mcpToolOrchestrator).executeTool(server, englishTranslation);
        verify(mcpToolOrchestrator).executeTool(server, spanishQuestion);
        verify(llmServiceEnhanced).generateWithMemory(eq("test-conv"), eq(spanishQuestion), 
                argThat(s -> s.contains("Contexto en inglés") && s.contains("Contexto en español")));
    }

    @Test
    void testMultilingualChat_EnglishQuestion() {
        // Given
        String englishQuestion = "What comedy movies are available?";
        String spanishTranslation = "¿Qué películas de comedia están disponibles?";
        String englishContext = "Comedy movies: [Superbad, Anchorman, Borat]";
        String spanishContext = "Películas de comedia: [Superbad, El Reportero, Borat]";
        String finalResponse = "Encontré estas películas de comedia: Superbad, El Reportero, y Borat.";
        
        McpRequest request = McpRequest.builder()
                .serverId("test-server")
                .message(englishQuestion)
                .conversationId("test-conv")
                .build();

        McpServer server = McpServer.builder()
                .id("test-server")
                .name("Test Movie Server")
                .status("CONNECTED")
                .build();

        // Mock server and tool responses
        when(mcpServerService.getServer("test-server")).thenReturn(Optional.of(server));
        
        // Mock translation
        when(llmServiceEnhanced.generateWithMemory(eq("translation_es"), anyString(), eq("")))
                .thenReturn(spanishTranslation);
        
        // Mock MCP tool execution for both languages
        when(mcpToolOrchestrator.executeTool(server, englishQuestion))
                .thenReturn(englishContext);
        when(mcpToolOrchestrator.executeTool(server, spanishTranslation))
                .thenReturn(spanishContext);
        
        // Mock LLM response generation
        when(llmServiceEnhanced.generateWithMemory(eq("test-conv"), eq(englishQuestion), anyString()))
                .thenReturn(finalResponse);

        // When
        ChatMessage response = chatService.sendMessage(request);

        // Then
        assertNotNull(response);
        assertEquals("ASSISTANT", response.getRole());
        assertEquals(finalResponse, response.getContent());
        assertEquals("test-server", response.getServerId());
        
        // Verify multilingual interaction
        verify(llmServiceEnhanced).generateWithMemory(eq("translation_es"), anyString(), eq(""));
        verify(mcpToolOrchestrator).executeTool(server, englishQuestion);
        verify(mcpToolOrchestrator).executeTool(server, spanishTranslation);
        verify(llmServiceEnhanced).generateWithMemory(eq("test-conv"), eq(englishQuestion), 
                argThat(s -> s.contains("Contexto en inglés") && s.contains("Contexto en español")));
    }

    @Test
    void testMultilingualChat_ServerNotConnected() {
        // Given
        McpRequest request = McpRequest.builder()
                .serverId("test-server")
                .message("Any question")
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
        assertTrue(response.getContent().contains("no está conectado"));
        assertEquals("test-server", response.getServerId());
        
        // Should not attempt translation or MCP calls
        verifyNoInteractions(mcpToolOrchestrator);
    }

    @Test
    void testMultilingualChat_LlmError_FallbackToFormatted() {
        // Given
        String question = "Show me movies";
        String context = "{\"movies\":[{\"title\":\"Avatar\",\"year\":\"2009\",\"genre\":\"Sci-Fi\"}]}";
        
        McpRequest request = McpRequest.builder()
                .serverId("test-server")
                .message(question)
                .conversationId("test-conv")
                .build();

        McpServer server = McpServer.builder()
                .id("test-server")
                .name("Test Movie Server")
                .status("CONNECTED")
                .build();

        when(mcpServerService.getServer("test-server")).thenReturn(Optional.of(server));
        when(mcpToolOrchestrator.executeTool(eq(server), anyString())).thenReturn(context);
        
        // Mock LLM error
        when(llmServiceEnhanced.generateWithMemory(anyString(), anyString(), anyString()))
                .thenReturn("Error generating response: Service unavailable");

        // When
        ChatMessage response = chatService.sendMessage(request);

        // Then
        assertNotNull(response);
        assertEquals("ASSISTANT", response.getRole());
        assertTrue(response.getContent().contains("Respuesta de Test Movie Server"));
        assertTrue(response.getContent().contains("Avatar"));
        assertEquals("test-server", response.getServerId());
    }
}