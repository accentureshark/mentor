package org.shark.mentor.mcp.integration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.shark.mentor.mcp.infraestructure.config.LlmProperties;
import org.shark.mentor.mcp.application.service.llm.LlmFactory;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test for streaming LLM Factory functionality
 */
public class StreamingIntegrationTest {

    @Test
    public void testStreamingModelCreation() {
        // Test that we can create streaming models without error
        LlmProperties props = new LlmProperties();
        props.setProvider("ollama");
        props.setModel("gemma2:2b");
        
        LlmProperties.Api api = new LlmProperties.Api();
        api.setBaseUrl("http://localhost:11434");
        props.setApi(api);
        
        LlmProperties.ModelConfig modelConfig = new LlmProperties.ModelConfig();
        modelConfig.setTemperature(0.7);
        modelConfig.setTimeoutMinutes(1);
        props.setModelConfig(modelConfig);
        
        // This should not throw an exception even if Ollama is not running
        assertDoesNotThrow(() -> {
            LlmFactory.createStreamingChatModel(
                props.getProvider(),
                props.getModel(),
                props.getApi().getBaseUrl(),
                props.getApi().getKey(),
                props.getModelConfig().getTemperature(),
                props.getModelConfig().getTimeoutMinutes()
            );
        });
    }

    @Test
    public void testStreamingCacheSize() {
        // Clear cache first
        LlmFactory.clearCache();
        assertEquals(0, LlmFactory.getCacheSize());
        
        // Create a streaming model
        LlmFactory.createStreamingChatModel("ollama", "gemma2:2b", "http://localhost:11434", null);
        
        // Cache should have one entry
        assertEquals(1, LlmFactory.getCacheSize());
        
        // Creating the same model again should not increase cache size
        LlmFactory.createStreamingChatModel("ollama", "gemma2:2b", "http://localhost:11434", null);
        assertEquals(1, LlmFactory.getCacheSize());
        
        // Creating a different model should increase cache size
        LlmFactory.createStreamingChatModel("ollama", "different:model", "http://localhost:11434", null);
        assertEquals(2, LlmFactory.getCacheSize());
    }
}