package org.shark.alma.mcp.service;

import org.shark.alma.mcp.model.ChatMessage;
import org.shark.alma.mcp.model.McpRequest;
import org.springframework.stereotype.Service;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service for handling chat messages and MCP communication
 */
@Service
@Slf4j
public class ChatService {
    
    private final Map<String, List<ChatMessage>> conversations = new ConcurrentHashMap<>();
    private final McpServerService mcpServerService;
    
    public ChatService(McpServerService mcpServerService) {
        this.mcpServerService = mcpServerService;
    }
    
    public List<ChatMessage> getConversation(String conversationId) {
        return conversations.getOrDefault(conversationId, new ArrayList<>());
    }
    
    public ChatMessage sendMessage(McpRequest request) {
        String conversationId = request.getConversationId();
        if (conversationId == null) {
            conversationId = "default";
        }
        
        // Add user message
        ChatMessage userMessage = ChatMessage.builder()
            .id(UUID.randomUUID().toString())
            .role("USER")
            .content(request.getMessage())
            .timestamp(System.currentTimeMillis())
            .serverId(request.getServerId())
            .build();
            
        addMessageToConversation(conversationId, userMessage);
        
        // Simulate MCP server response
        ChatMessage assistantMessage = simulateMcpResponse(request);
        addMessageToConversation(conversationId, assistantMessage);
        
        return assistantMessage;
    }
    
    private void addMessageToConversation(String conversationId, ChatMessage message) {
        conversations.computeIfAbsent(conversationId, k -> new ArrayList<>()).add(message);
        log.info("Added message to conversation {}: {}", conversationId, message.getContent());
    }
    
    private ChatMessage simulateMcpResponse(McpRequest request) {
        // This is a simple simulation of MCP server response
        // In a real implementation, this would communicate with actual MCP servers
        
        String response = generateMockResponse(request);
        
        return ChatMessage.builder()
            .id(UUID.randomUUID().toString())
            .role("ASSISTANT")
            .content(response)
            .timestamp(System.currentTimeMillis())
            .serverId(request.getServerId())
            .build();
    }
    
    private String generateMockResponse(McpRequest request) {
        String serverId = request.getServerId();
        String message = request.getMessage().toLowerCase();
        
        // Mock responses based on server type and message content
        switch (serverId) {
            case "local-file-server":
                if (message.contains("list") || message.contains("ls")) {
                    return "📁 Files found:\n- document.txt\n- image.jpg\n- data.json\n- script.py";
                } else if (message.contains("read") || message.contains("cat")) {
                    return "📄 File content:\nHello, this is a sample file content from the local file server.";
                }
                return "🔧 Local File Server: I can help you list files, read file contents, or manage your local filesystem.";
                
            case "web-search-server":
                if (message.contains("search") || message.contains("find")) {
                    return "🔍 Search results:\n1. Example result 1 - www.example1.com\n2. Example result 2 - www.example2.com\n3. Example result 3 - www.example3.com";
                }
                return "🌐 Web Search Server: I can help you search the web for information. Try asking me to search for something!";
                
            case "database-server":
                if (message.contains("query") || message.contains("select")) {
                    return "💾 Query results:\n| ID | Name | Status |\n|----|----- |--------|\n| 1  | Item A | Active |\n| 2  | Item B | Inactive |";
                }
                return "🗄️ Database Server: I can help you query databases and retrieve information. Try asking me to run a query!";
                
            default:
                return "🤖 MCP Server Response: I received your message: \"" + request.getMessage() + "\". How can I help you?";
        }
    }
    
    public List<String> getConversationIds() {
        return new ArrayList<>(conversations.keySet());
    }
    
    public void clearConversation(String conversationId) {
        conversations.remove(conversationId);
        log.info("Cleared conversation: {}", conversationId);
    }
}