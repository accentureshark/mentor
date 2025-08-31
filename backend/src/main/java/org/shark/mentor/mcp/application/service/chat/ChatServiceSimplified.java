package org.shark.mentor.mcp.application.service.chat;

import lombok.extern.slf4j.Slf4j;
import org.shark.mentor.mcp.application.service.formatting.ResponseFormatterService;
import org.shark.mentor.mcp.application.service.llm.LlmServiceEnhanced;
import org.shark.mentor.mcp.application.service.server.McpServerService;
import org.shark.mentor.mcp.application.service.tool.McpToolOrchestrator;
import org.shark.mentor.mcp.domain.model.ChatMessage;
import org.shark.mentor.mcp.domain.model.McpRequest;
import org.shark.mentor.mcp.domain.model.McpServer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Simplified chat service using langchain4j and the new MCP tool orchestrator
 * with optimized response formatting to reduce LLM calls for common patterns
s */
@Service
@Slf4j
public class ChatServiceSimplified {

    private final Map<String, List<ChatMessage>> conversations = new ConcurrentHashMap<>();
    private final McpServerService mcpServerService;
    private final McpToolOrchestrator mcpToolOrchestrator;
    private final LlmServiceEnhanced llmService;
    private final ResponseFormatterService responseFormatterService;

    private boolean enableTemplateFormatting = true;
    private boolean templateFirst = true;

    public ChatServiceSimplified(McpServerService mcpServerService, 
                                McpToolOrchestrator mcpToolOrchestrator,
                                LlmServiceEnhanced llmService,
                                ResponseFormatterService responseFormatterService) {
        this.mcpServerService = mcpServerService;
        this.mcpToolOrchestrator = mcpToolOrchestrator;
        this.llmService = llmService;
        this.responseFormatterService = responseFormatterService;
    }

    @Value("${llm.formatting.enable-template-formatting:true}")
    public void setEnableTemplateFormatting(boolean enableTemplateFormatting) {
        this.enableTemplateFormatting = enableTemplateFormatting;
    }

    @Value("${llm.formatting.template-first:true}")
    public void setTemplateFirst(boolean templateFirst) {
        this.templateFirst = templateFirst;
    }

    public List<ChatMessage> getConversation(String conversationId) {
        return conversations.getOrDefault(conversationId, new ArrayList<>());
    }

    public List<String> getConversationIds() {
        return new ArrayList<>(conversations.keySet());
    }

    public void clearConversation(String conversationId) {
        conversations.remove(conversationId);
        llmService.clearConversation(conversationId);
        log.info("Cleared conversation: {}", conversationId);
    }

    public ChatMessage sendMessage(McpRequest request) {
        String conversationId = request.getConversationId();
        if (conversationId == null) {
            conversationId = "default";
        }

        // Validate server exists and is connected
        Optional<McpServer> serverOpt = mcpServerService.getServer(request.getServerId());
        if (serverOpt.isEmpty()) {
            return createErrorMessage(request, "Server not found: " + request.getServerId());
        }

        McpServer server = serverOpt.get();
        if (!"CONNECTED".equals(server.getStatus())) {
            return createErrorMessage(request, "Server is not connected: " + server.getName());
        }

        // Create and store user message
        ChatMessage userMessage = ChatMessage.builder()
                .id(UUID.randomUUID().toString())
                .role("USER")
                .content(request.getMessage())
                .timestamp(System.currentTimeMillis())
                .serverId(request.getServerId())
                .build();

        addMessageToConversation(conversationId, userMessage);

        try {
            // Use the MCP tool orchestrator to get context from the server
            String mcpContext = mcpToolOrchestrator.executeTool(server, request.getMessage());
            
            String assistantContent;
            boolean usedTemplate = false;
            
            // Try template-based formatting first if enabled
            if (enableTemplateFormatting && templateFirst) {
                assistantContent = responseFormatterService.tryFormatWithoutLlm(
                    mcpContext, request.getMessage(), server);
                
                if (assistantContent != null) {
                    usedTemplate = true;
                    log.debug("Used template-based formatting for conversation {} (avoiding LLM call)", conversationId);
                } else {
                    // Fall back to LLM if template formatting wasn't suitable
                    assistantContent = llmService.generateWithMemory(conversationId, request.getMessage(), mcpContext, server);
                    log.debug("Template formatting not suitable, used LLM for conversation {}", conversationId);
                }
            } else {
                // Use LLM directly if template formatting is disabled
                assistantContent = llmService.generateWithMemory(conversationId, request.getMessage(), mcpContext, server);
            }
            
            // Create assistant response message
            ChatMessage assistantMessage = ChatMessage.builder()
                    .id(UUID.randomUUID().toString())
                    .role("ASSISTANT")
                    .content(assistantContent)
                    .timestamp(System.currentTimeMillis())
                    .serverId(request.getServerId())
                    .build();

            addMessageToConversation(conversationId, assistantMessage);
            
            if (usedTemplate) {
                log.info("Successfully processed message for conversation {} using server {} (template formatting)", 
                        conversationId, server.getName());
            } else {
                log.info("Successfully processed message for conversation {} using server {} (LLM formatting)", 
                        conversationId, server.getName());
            }
            
            return assistantMessage;

        } catch (Exception e) {
            log.error("Error processing message for conversation {}: {}", conversationId, e.getMessage(), e);
            return createErrorMessage(request, "Error processing message: " + e.getMessage());
        }
    }

    private ChatMessage createErrorMessage(McpRequest request, String errorMessage) {
        return ChatMessage.builder()
                .id(UUID.randomUUID().toString())
                .role("ASSISTANT")
                .content(errorMessage)
                .timestamp(System.currentTimeMillis())
                .serverId(request.getServerId())
                .build();
    }

    private void addMessageToConversation(String conversationId, ChatMessage message) {
        conversations.computeIfAbsent(conversationId, k -> new ArrayList<>()).add(message);
        log.debug("Added message to conversation {}: {} characters", 
                conversationId, message.getContent().length());
    }
}