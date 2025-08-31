package org.shark.mentor.mcp.service;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.output.Response;
import org.junit.jupiter.api.Test;
import org.shark.mentor.mcp.application.service.cache.LlmResponseCache;
import org.shark.mentor.mcp.application.service.i18n.I18nService;
import org.shark.mentor.mcp.application.service.llm.LlmServiceEnhanced;
import org.shark.mentor.mcp.application.service.notification.DynamicToolInfoService;
import org.shark.mentor.mcp.application.service.tool.McpToolService;
import org.shark.mentor.mcp.application.service.tool.ToolContextCache;
import org.shark.mentor.mcp.infraestructure.config.LlmProperties;
import org.shark.mentor.mcp.infraestructure.config.UiProperties;


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
        LlmResponseCache responseCache = new LlmResponseCache();
        LlmServiceEnhanced service = new LlmServiceEnhanced(props, i18nService, dynamicToolInfoService, toolContextCache, responseCache);

        ChatLanguageModel model = mock(ChatLanguageModel.class);
        var captor = forClass(List.class);
        when(model.generate(captor.capture())).thenAnswer(invocation -> {
            List<ChatMessage> messages = invocation.getArgument(0);
            String systemText = ((SystemMessage) messages.get(0)).text();
            String content = systemText.contains("locale: es")
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
        assertTrue(systemText.contains("locale: es"));
        assertTrue(systemText.contains("user's preferred language") || systemText.contains("locale:"));
    }
}
