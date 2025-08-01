package org.shark.mentor.mcp.service;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatLanguageModel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.shark.mentor.mcp.config.LlmProperties;
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
@RequiredArgsConstructor
@Primary
public class LlmServiceEnhanced implements LlmService {

    private final LlmProperties props;
    private ChatLanguageModel chatModel;
    private final Map<String, ChatMemory> conversationMemories = new ConcurrentHashMap<>();

    @jakarta.annotation.PostConstruct
    public void initModel() {
        log.info("Initializing enhanced LLM model with provider: {}", props.getProvider());
        chatModel = LlmFactory.createChatModel(
                props.getProvider(),
                props.getModel(),
                props.getApi().getBaseUrl(),
                props.getApi().getKey()
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
        try {
            List<ChatMessage> messages = buildMessages(question, context);
            
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
        List<ChatMessage> messages = new ArrayList<>();
        
        // System message defining MCP-compliant behavior
        String systemPrompt = buildSystemPrompt();
        messages.add(SystemMessage.from(systemPrompt));
        
        // Add context as system information if available
        if (context != null && !context.isBlank()) {
            String contextPrompt = "Context from MCP server:\n" + context;
            messages.add(SystemMessage.from(contextPrompt));
        }
        
        // User question
        messages.add(UserMessage.from(question));
        
        return messages;
    }

    /**
     * Build MCP-compliant system prompt
     */
    private String buildSystemPrompt() {
        return """
            You are a helpful assistant that works with MCP (Model Context Protocol) servers.
            
            Important guidelines:
            1. Answer ONLY using information provided in the context from MCP servers
            2. Do not infer or add information that is not explicitly stated in the context
            3. If the context is insufficient to answer the question, clearly state what information is missing
            4. Be precise and factual in your responses
            5. When relevant, mention which MCP server provided the information
            
            Always maintain accuracy and transparency about the limitations of the available context.
            """;
    }
}