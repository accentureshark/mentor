package org.shark.mentor.mcp.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.shark.mentor.mcp.application.service.cache.LlmResponseCache;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class LlmResponseCacheTest {

    private LlmResponseCache cache;

    @BeforeEach
    void setUp() {
        cache = new LlmResponseCache();
    }

    @Test
    void shouldCacheAndRetrieveResponse() {
        String question = "What is the weather?";
        String context = "Weather data: sunny, 25°C";
        String response = "It's sunny and 25 degrees Celsius.";

        // Cache response
        cache.put(question, context, response);

        // Retrieve response
        String retrieved = cache.get(question, context);
        assertEquals(response, retrieved);
    }

    @Test
    void shouldReturnNullForNonExistentKey() {
        String retrieved = cache.get("non-existent question", "context");
        assertNull(retrieved);
    }

    @Test
    void shouldHandleNullAndEmptyInputs() {
        // Should not store null or empty responses
        cache.put("question", "context", null);
        cache.put("question", "context", "");
        cache.put("question", "context", "   ");

        assertNull(cache.get("question", "context"));
    }

    @Test
    void shouldClearCache() {
        cache.put("q1", "c1", "r1");
        cache.put("q2", "c2", "r2");
        
        assertEquals(2, cache.size());
        
        cache.clear();
        assertEquals(0, cache.size());
    }

    @Test
    void shouldInvalidateSpecificEntry() {
        String question = "test question";
        String context = "test context";
        
        cache.put(question, context, "test response");
        assertNotNull(cache.get(question, context));
        
        cache.invalidate(question, context);
        assertNull(cache.get(question, context));
    }

    @Test
    void shouldGenerateConsistentKeys() {
        String question = "What is the weather?";
        String context = "Weather data";
        
        cache.put(question, context, "response1");
        cache.put(question, context, "response2"); // Should overwrite
        
        String retrieved = cache.get(question, context);
        assertEquals("response2", retrieved);
    }

    @Test
    void shouldHandleDifferentContextsSeparately() {
        String question = "What is the weather?";
        String context1 = "Weather data: sunny";
        String context2 = "Weather data: rainy";
        
        cache.put(question, context1, "sunny response");
        cache.put(question, context2, "rainy response");
        
        assertEquals("sunny response", cache.get(question, context1));
        assertEquals("rainy response", cache.get(question, context2));
    }
}