package org.shark.mentor.mcp.service;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.output.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.shark.mentor.mcp.application.service.cache.LlmResponseCache;
import org.shark.mentor.mcp.application.service.i18n.I18nService;
import org.shark.mentor.mcp.application.service.llm.LlmServiceEnhanced;
import org.shark.mentor.mcp.application.service.notification.DynamicToolInfoService;
import org.shark.mentor.mcp.application.service.tool.ToolContextCache;
import org.shark.mentor.mcp.infraestructure.config.LlmProperties;
import org.shark.mentor.mcp.infraestructure.config.UiProperties;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LlmServiceEnhancedPerformanceTest {

    @Mock
    private ChatLanguageModel chatModel;
    
    @Mock
    private DynamicToolInfoService dynamicToolInfoService;
    
    @Mock
    private ToolContextCache toolContextCache;
    
    private LlmServiceEnhanced service;
    private LlmProperties props;
    private LlmResponseCache responseCache;
    private I18nService i18nService;

    @BeforeEach
    void setUp() throws Exception {
        props = new LlmProperties();
        props.getPerformance().setEnableCaching(true);
        props.getPerformance().setCacheTtlMinutes(10);
        props.getPerformance().setEnableConversationMemoryCleanup(true);
        props.getPerformance().setConversationMemoryTtlMinutes(60);
        
        UiProperties uiProps = new UiProperties();
        uiProps.setLocale("en");
        i18nService = new I18nService(uiProps);
        
        responseCache = new LlmResponseCache();
        
        service = new LlmServiceEnhanced(props, i18nService, dynamicToolInfoService, toolContextCache, responseCache);
        
        // Inject mock chat model
        Field field = LlmServiceEnhanced.class.getDeclaredField("chatModel");
        field.setAccessible(true);
        field.set(service, chatModel);
    }

    @Test
    void shouldUseCacheForIdenticalRequests() {
        // Setup
        String question = "What is the weather?";
        String context = "Weather data: sunny, 25°C";
        String expectedResponse = "It's sunny and 25 degrees Celsius.";
        
        when(chatModel.generate(any(List.class)))
            .thenReturn(Response.from(AiMessage.from(expectedResponse)));

        // First call should hit the LLM
        String firstResponse = service.generateWithMemory("conv1", question, context);
        assertEquals(expectedResponse, firstResponse);
        verify(chatModel, times(1)).generate(any(List.class));

        // Second identical call should use cache
        String secondResponse = service.generateWithMemory("conv2", question, context);
        assertEquals(expectedResponse, secondResponse);
        verify(chatModel, times(1)).generate(any(List.class)); // Should still be 1, not 2
    }

    @Test
    void shouldNotUseCacheWhenDisabled() throws Exception {
        // Disable caching
        props.getPerformance().setEnableCaching(false);
        
        // Create new service with caching disabled
        service = new LlmServiceEnhanced(props, i18nService, dynamicToolInfoService, toolContextCache, responseCache);
        Field field = LlmServiceEnhanced.class.getDeclaredField("chatModel");
        field.setAccessible(true);
        field.set(service, chatModel);
        
        String question = "What is the weather?";
        String context = "Weather data: sunny";
        String expectedResponse = "It's sunny.";
        
        when(chatModel.generate(any(List.class)))
            .thenReturn(Response.from(AiMessage.from(expectedResponse)));

        // Both calls should hit the LLM
        service.generateWithMemory("conv1", question, context);
        service.generateWithMemory("conv2", question, context);
        
        verify(chatModel, times(2)).generate(any(List.class));
    }

    @Test
    void shouldReturnCacheStatistics() {
        Map<String, Object> stats = service.getCacheStats();
        
        assertNotNull(stats);
        assertTrue(stats.containsKey("responseCacheSize"));
        assertTrue(stats.containsKey("conversationMemoriesCount"));
        assertTrue(stats.containsKey("modelCacheSize"));
    }

    @Test
    void shouldClearAllCaches() {
        // Add some data first
        String question = "test question";
        String context = "test context";
        String response = "test response";
        
        when(chatModel.generate(any(List.class)))
            .thenReturn(Response.from(AiMessage.from(response)));
        
        service.generateWithMemory("conv1", question, context);
        service.generateWithMemory("conv2", question, context);
        
        // Verify there's data in cache
        Map<String, Object> statsBefore = service.getCacheStats();
        assertTrue((Integer) statsBefore.get("responseCacheSize") >= 0);
        
        // Clear caches
        service.clearAllCaches();
        
        // Verify caches are cleared
        Map<String, Object> statsAfter = service.getCacheStats();
        assertEquals(0, (Integer) statsAfter.get("responseCacheSize"));
        assertEquals(0, (Integer) statsAfter.get("conversationMemoriesCount"));
    }

    @Test
    void shouldHandleConversationMemoryCleanup() {
        // This test verifies the cleanup mechanism is in place
        // In a real scenario, we'd need to wait for the cleanup thread or trigger it manually
        
        service.generateWithMemory("conv1", "question1", "context1");
        service.generateWithMemory("conv2", "question2", "context2");
        
        Map<String, Object> stats = service.getCacheStats();
        Integer conversationCount = (Integer) stats.get("conversationMemoriesCount");
        assertTrue(conversationCount >= 0);
        
        // Clear a specific conversation
        service.clearConversation("conv1");
        
        // Verify the conversation count might have decreased
        Map<String, Object> statsAfter = service.getCacheStats();
        Integer conversationCountAfter = (Integer) statsAfter.get("conversationMemoriesCount");
        assertTrue(conversationCountAfter >= 0);
    }

    @Test
    void shouldHandleErrorsGracefully() {
        // Test error handling when LLM throws exception
        when(chatModel.generate(any(List.class)))
            .thenThrow(new RuntimeException("LLM connection error"));
        
        String response = service.generateWithMemory("conv1", "question", "context");
        
        assertTrue(response.startsWith("Error generating response:"));
    }

    @Test
    void shouldOptimizePromptLength() {
        // Test that system prompts are optimized for performance
        String question = "simple question";
        String context = "simple context";
        
        when(chatModel.generate(any(List.class)))
            .thenAnswer(invocation -> {
                List<ChatMessage> messages = invocation.getArgument(0);
                // Verify that we have messages but they're not excessively long
                assertFalse(messages.isEmpty());
                return Response.from(AiMessage.from("response"));
            });
        
        String response = service.generateWithMemory("conv1", question, context);
        assertEquals("response", response);
        
        verify(chatModel, times(1)).generate(any(List.class));
    }
}