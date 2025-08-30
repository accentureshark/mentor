package org.shark.mentor.mcp.service;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.output.Response;
import org.junit.jupiter.api.Test;
import org.shark.mentor.mcp.config.LlmProperties;
import org.shark.mentor.mcp.config.UiProperties;

import java.lang.reflect.Field;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.Mockito.*;

class LlmServiceEnhancedTest {

    @Test
    void generateWithMemoryReturnsLocalizedResponse() throws Exception {
        LlmProperties props = new LlmProperties();
        UiProperties uiProps = new UiProperties();
        uiProps.setLocale("es");
        I18nService i18nService = new I18nService(uiProps);
        McpToolService mcpToolService = mock(McpToolService.class);
        DynamicToolInfoService dynamicToolInfoService = new DynamicToolInfoService(mcpToolService);
        ToolContextCache toolContextCache = new ToolContextCache(dynamicToolInfoService);
        LlmServiceEnhanced service = new LlmServiceEnhanced(props, i18nService, dynamicToolInfoService, toolContextCache);

        ChatLanguageModel model = mock(ChatLanguageModel.class);
        var captor = forClass(List.class);
        when(model.generate(captor.capture())).thenAnswer(invocation -> {
            List<ChatMessage> messages = invocation.getArgument(0);
            String systemText = ((SystemMessage) messages.get(0)).text();
            String content = systemText.contains("current locale: es")
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
        assertTrue(systemText.contains("current locale: es"));
        assertTrue(systemText.contains("user's preferred language"));
        assertFalse(systemText.contains("ALWAYS respond in Spanish"));
    }
}
