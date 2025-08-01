package org.shark.mentor.mcp.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.shark.mentor.mcp.model.ChatMessage;
import org.shark.mentor.mcp.model.McpRequest;
import org.shark.mentor.mcp.model.McpServer;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Simplified chat service using langchain4j and the new MCP tool orchestrator
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class ChatServiceSimplified {

    private final Map<String, List<ChatMessage>> conversations = new ConcurrentHashMap<>();
    private final McpServerService mcpServerService;
    private final McpToolOrchestrator mcpToolOrchestrator;
    private final LlmServiceEnhanced llmService;

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
            
            // Use the enhanced LLM service to generate response with conversation memory
            String assistantContent = llmService.generateWithMemory(conversationId, request.getMessage(), mcpContext);
            
            // Create assistant response message
            ChatMessage assistantMessage = ChatMessage.builder()
                    .id(UUID.randomUUID().toString())
                    .role("ASSISTANT")
                    .content(assistantContent)
                    .timestamp(System.currentTimeMillis())
                    .serverId(request.getServerId())
                    .build();

            addMessageToConversation(conversationId, assistantMessage);
            
            log.info("Successfully processed message for conversation {} using server {}", 
                    conversationId, server.getName());
            
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