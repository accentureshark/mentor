package org.shark.mentor.mcp.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.shark.mentor.mcp.application.service.llm.StreamingLlmService;
import org.shark.mentor.mcp.application.service.llm.StreamingLlmServiceImpl;
import org.shark.mentor.mcp.infraestructure.config.LlmProperties;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Test for streaming LLM functionality
 */
public class StreamingLlmServiceTest {

    private StreamingLlmService streamingService;
    private LlmProperties mockProps;

    @BeforeEach
    public void setUp() {
        mockProps = new LlmProperties();
        mockProps.setProvider("ollama");
        mockProps.setModel("gemma2:2b");
        
        // Configure API settings
        LlmProperties.Api api = new LlmProperties.Api();
        api.setBaseUrl("http://localhost:11434");
        mockProps.setApi(api);
        
        // Configure model settings
        LlmProperties.ModelConfig modelConfig = new LlmProperties.ModelConfig();
        modelConfig.setTemperature(0.7);
        modelConfig.setTimeoutMinutes(1);
        mockProps.setModelConfig(modelConfig);
        
        // Configure performance settings with streaming disabled for unit test
        LlmProperties.Performance performance = new LlmProperties.Performance();
        performance.setEnableStreaming(false); // Disable for unit test
        performance.setStreamingTimeoutMillis(30000L);
        mockProps.setPerformance(performance);
        
        streamingService = new StreamingLlmServiceImpl(mockProps);
    }

    @Test
    public void testStreamingDisabled() {
        // When streaming is disabled, should throw exception
        assertThrows(IllegalStateException.class, () -> {
            streamingService.generateStreaming("Test question", "Test context");
        });
    }

    @Test
    public void testStreamingConfigurationEnabled() {
        // Enable streaming for this test
        mockProps.getPerformance().setEnableStreaming(true);
        
        StreamingLlmServiceImpl service = new StreamingLlmServiceImpl(mockProps);
        
        // Verify streaming is configured to be available
        assertTrue(mockProps.getPerformance().isEnableStreaming());
        assertEquals(30000L, mockProps.getPerformance().getStreamingTimeoutMillis());
    }

    @Test
    public void testStreamingLlmServiceCreation() {
        // Test that service can be created without errors
        assertNotNull(streamingService);
        assertTrue(streamingService instanceof StreamingLlmServiceImpl);
    }
}