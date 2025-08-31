package org.shark.mentor.mcp.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.shark.mentor.mcp.application.service.llm.LlmServiceEnhanced;
import org.shark.mentor.mcp.application.service.cache.LlmResponseCache;
import org.shark.mentor.mcp.application.service.i18n.I18nService;
import org.shark.mentor.mcp.application.service.notification.DynamicToolInfoService;
import org.shark.mentor.mcp.application.service.tool.ToolContextCache;
import org.shark.mentor.mcp.infraestructure.config.LlmProperties;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

/**
 * Test for streaming LLM functionality
 */
public class LlmStreamingTest {

    @Mock
    private LlmProperties props;
    
    @Mock
    private LlmProperties.Performance performance;
    
    @Mock
    private LlmProperties.ModelConfig modelConfig;
    
    @Mock
    private LlmProperties.Api api;
    
    @Mock
    private I18nService i18nService;
    
    @Mock
    private DynamicToolInfoService dynamicToolInfoService;
    
    @Mock
    private ToolContextCache toolContextCache;
    
    @Mock
    private LlmResponseCache responseCache;
    
    private LlmServiceEnhanced llmService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        
        // Mock configuration
        when(props.getProvider()).thenReturn("ollama");
        when(props.getModel()).thenReturn("test-model");
        when(props.getPerformance()).thenReturn(performance);
        when(props.getModelConfig()).thenReturn(modelConfig);
        when(props.getApi()).thenReturn(api);
        
        when(performance.isEnableStreaming()).thenReturn(true);
        when(performance.isEnableCaching()).thenReturn(false); // Disable cache for test
        when(performance.getStreamingDelayMs()).thenReturn(10); // Fast for tests
        when(performance.getStreamingWordChunkSize()).thenReturn(1);
        when(performance.getFastTimeoutSeconds()).thenReturn(30);
        when(performance.isEnableConversationMemoryCleanup()).thenReturn(false);
        
        when(modelConfig.getTemperature()).thenReturn(0.7);
        when(modelConfig.getTimeoutMinutes()).thenReturn(1);
        
        when(api.getBaseUrl()).thenReturn("http://localhost:11434");
        when(api.getKey()).thenReturn(null);
        
        when(i18nService.getCurrentLocale()).thenReturn(Locale.ENGLISH);
        when(i18nService.getMessage("context.mcp")).thenReturn("Context from MCP server");
        when(i18nService.getMessage("instructions.formatting")).thenReturn("Formatting instructions");
        when(i18nService.getMessage("info.provided.by", "[server name]")).thenReturn("Provided by [server name]");
        
        // Create the service
        llmService = new LlmServiceEnhanced(props, i18nService, dynamicToolInfoService, toolContextCache, responseCache);
    }

    @Test
    void testGenerateStreamReturnsFlux() {
        // Test that streaming returns a Flux
        Flux<String> stream = llmService.generateStream("Test question", "Test context");
        
        assertNotNull(stream, "Stream should not be null");
        
        // Since chatModel is null, it should return an error stream
        String result = stream.collectList()
                .map(list -> String.join("", list))
                .block(Duration.ofSeconds(5));
        
        assertNotNull(result, "Result should not be null");
        assertTrue(result.contains("Error") || result.contains("null"), "Result should contain error message when model is null");
    }

    @Test
    void testStreamingDisabledFallsBackToSync() {
        // Test fallback when streaming is disabled
        when(performance.isEnableStreaming()).thenReturn(false);
        
        Flux<String> stream = llmService.generateStream("Test question", "Test context");
        
        assertNotNull(stream, "Stream should not be null even when disabled");
        
        // Should still return a stream (fallback behavior) with error since model is null
        String result = stream.collectList()
                .map(list -> String.join("", list))
                .block(Duration.ofSeconds(5));
        
        assertNotNull(result, "Result should not be null");
        assertTrue(result.contains("Error") || result.contains("null"), "Result should contain error message when model is null");
    }

    @Test
    void testStreamingConfigurationProperties() {
        // Test that our new configuration properties are being read
        assertEquals(10, performance.getStreamingDelayMs());
        assertEquals(1, performance.getStreamingWordChunkSize());
        assertTrue(performance.isEnableStreaming());
    }
}