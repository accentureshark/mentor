package org.shark.mentor.mcp.service;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.output.Response;
import org.junit.jupiter.api.Test;
import org.shark.mentor.mcp.config.LlmProperties;

import java.lang.reflect.Field;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.Mockito.*;

class LlmServiceEnhancedTest {

    @Test
    void generateWithMemoryReturnsSpanishResponse() throws Exception {
        LlmProperties props = new LlmProperties();
        LlmServiceEnhanced service = new LlmServiceEnhanced(props);

        ChatLanguageModel model = mock(ChatLanguageModel.class);
        var captor = forClass(List.class);
        when(model.generate(captor.capture())).thenAnswer(invocation -> {
            List<ChatMessage> messages = invocation.getArgument(0);
            String systemText = ((SystemMessage) messages.get(0)).text();
            String content = systemText.contains("All responses must be in Spanish")
                    ? "Respuesta en español" : "English response";
            return Response.from(AiMessage.from(content));
        });

        Field field = LlmServiceEnhanced.class.getDeclaredField("chatModel");
        field.setAccessible(true);
        field.set(service, model);

        String result = service.generateWithMemory("conv", "Hello", null);

        assertEquals("Respuesta en español", result);
        List<ChatMessage> messages = captor.getValue();
        String systemText = ((SystemMessage) messages.get(0)).text();
        assertTrue(systemText.contains("ALWAYS respond in Spanish"));
        assertTrue(systemText.contains("All responses must be in Spanish"));
    }
}
