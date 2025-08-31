package org.shark.mentor.mcp.application.service.llm;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatLanguageModel;
import lombok.extern.slf4j.Slf4j;
import org.shark.mentor.mcp.application.service.i18n.I18nService;
import org.shark.mentor.mcp.application.service.notification.DynamicToolInfoService;
import org.shark.mentor.mcp.application.service.tool.ToolContextCache;
import org.shark.mentor.mcp.infraestructure.config.LlmProperties;
import org.shark.mentor.mcp.domain.model.McpServer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

/**
 * Enhanced LLM service using langchain4j best practices for conversation management
 * and MCP-compliant response generation
 */
@Slf4j
@Service("llmServiceEnhanced")
@Primary
public class LlmServiceEnhanced implements LlmService {

    private final LlmProperties props;
    private final I18nService i18nService;
    private final DynamicToolInfoService dynamicToolInfoService;
    private final ToolContextCache toolContextCache;
    private ChatLanguageModel chatModel;
    private final Map<String, ChatMemory> conversationMemories = new ConcurrentHashMap<>();
    
    // Primary constructor
    @Autowired
    public LlmServiceEnhanced(LlmProperties props, I18nService i18nService, 
                             DynamicToolInfoService dynamicToolInfoService,
                             ToolContextCache toolContextCache) {
        this.props = props;
        this.i18nService = i18nService;
        this.dynamicToolInfoService = dynamicToolInfoService;
        this.toolContextCache = toolContextCache;
    }

    @jakarta.annotation.PostConstruct
    public void initModel() {
        log.info("Initializing enhanced LLM model with provider: {}", props.getProvider());
        chatModel = LlmFactory.createChatModel(
                props.getProvider(),
                props.getModel(),
                props.getApi().getBaseUrl(),
                props.getApi().getKey(),
                props.getModelConfig().getTemperature(),
                props.getModelConfig().getTimeoutMinutes()
        );
        log.info("Enhanced LLM model initialized successfully");
    }

    @Override
    public String generate(String question, String context) {
        return generateWithMemory("default", question, context);
    }

    /**
     * Generate response with conversation memory support
     */
    public String generateWithMemory(String conversationId, String question, String context) {
        return generateWithMemory(conversationId, question, context, null);
    }

    /**
     * Generate response with conversation memory support and MCP server context for dynamic formatting
     */
    public String generateWithMemory(String conversationId, String question, String context, McpServer server) {
        try {
            List<ChatMessage> messages = buildMessages(question, context, server);
            
            // Use langchain4j to generate response with proper context management
            String response = chatModel.generate(messages).content().text();
            
            log.debug("Generated response for conversation {}: {}", conversationId, response);
            return response;
            
        } catch (Exception e) {
            log.error("Error generating LLM response for conversation {}: {}", conversationId, e.getMessage(), e);
            return "Error generating response: " + e.getMessage();
        }
    }

    /**
     * Get or create conversation memory for a specific conversation
     */
    private ChatMemory getConversationMemory(String conversationId) {
        return conversationMemories.computeIfAbsent(conversationId, 
            k -> MessageWindowChatMemory.withMaxMessages(20));
    }

    /**
     * Clear conversation memory for a specific conversation
     */
    public void clearConversation(String conversationId) {
        conversationMemories.remove(conversationId);
        log.info("Cleared conversation memory for: {}", conversationId);
    }

    /**
     * Build proper message list for langchain4j processing
     */
    private List<ChatMessage> buildMessages(String question, String context) {
        return buildMessages(question, context, null);
    }

    /**
     * Build proper message list for langchain4j processing with optional server context
     */
    private List<ChatMessage> buildMessages(String question, String context, McpServer server) {
        List<ChatMessage> messages = new ArrayList<>();
        
        // System message defining MCP-compliant behavior
        String systemPrompt = buildSystemPrompt();
        messages.add(SystemMessage.from(systemPrompt));
        
        // Add context as system information if available
        if (context != null && !context.isBlank()) {
            String contextPrompt = buildContextPrompt(context, question, server);
            messages.add(SystemMessage.from(contextPrompt));
        }
        
        // User question
        messages.add(UserMessage.from(question));
        
        return messages;
    }

    /**
     * Build context prompt that instructs the LLM how to format the response based on MCP tool results
     */
    private String buildContextPrompt(String context, String question) {
        return buildContextPrompt(context, question, null);
    }

    /**
     * Build context prompt that dynamically determines formatting based on MCP server tools
     */
    private String buildContextPrompt(String context, String question, McpServer server) {
        if (server != null) {
            // Use comprehensive context with caching to reduce LLM calls
            return toolContextCache.buildComprehensivePrompt(server, context, question);
        } else {
            // Fallback to basic context when no server is available
            StringBuilder prompt = new StringBuilder();
            prompt.append(i18nService.getMessage("context.mcp")).append(":\n");
            prompt.append(context);
            prompt.append("\n\n").append(i18nService.getMessage("instructions.formatting")).append(":\n");
            prompt.append("Organize the information clearly with:\n");
            prompt.append("- Descriptive titles with appropriate emojis\n");
            prompt.append("- Information structured in lists\n");
            prompt.append("- Use of markdown for formatting\n");
            prompt.append("- Clear separation between elements\n");
            prompt.append("\nAlways end with: ").append(i18nService.getMessage("info.provided.by", "[server name]"));
            return prompt.toString();
        }
    }

    /**
     * Build MCP-compliant system prompt that ensures localized responses and focuses on tool understanding
     */
    private String buildSystemPrompt() {
        String currentLocale = i18nService.getCurrentLocale().toString();
        
        return "You are a helpful assistant that works with MCP (Model Context Protocol) servers.\n" +
            "You specialize in understanding and executing tool requests across different domains.\n\n" +
            "Important guidelines:\n" +
            "1. Respond ONLY using information provided in the context of MCP servers\n" +
            "2. Do not infer or add information that is not explicitly indicated in the context\n" +
            "3. If the context is insufficient to answer the question, clearly state what information is missing\n" +
            "4. Be precise and factual in your responses\n" +
            "5. When relevant, mention which MCP server provided the information\n" +
            "6. Respond in the user's preferred language (current locale: " + currentLocale + ")\n" +
            "7. Focus on helping users understand the capabilities and results of MCP tools\n\n" +
            "RESPONSE FORMAT:\n" +
            "- Use clear titles and subtitles with appropriate emojis\n" +
            "- Follow any specific formatting instructions provided in the context\n" +
            "- Organize information in numbered or bulleted lists\n" +
            "- Use proper spacing between sections\n" +
            "- If there are multiple results, list them clearly\n" +
            "- Use markdown formatting for better readability\n\n" +
            "Always maintain accuracy and transparency about the limitations of the available context.\n" +
            "All responses must be well formatted and in the user's preferred language.\n" +
            "Adapt the format based on the type of MCP server and tool being used.";
    }
}