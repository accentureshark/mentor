package org.shark.alma.mcp.controller;

import org.shark.alma.mcp.model.ChatMessage;
import org.shark.alma.mcp.model.McpRequest;
import org.shark.alma.mcp.service.ChatService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

/**
 * REST controller for chat functionality
 */
@RestController
@RequestMapping("/api/mcp/chat")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
@Slf4j
public class ChatController {
    
    private final ChatService chatService;
    
    @GetMapping("/conversations")
    public ResponseEntity<List<String>> getConversations() {
        log.info("Getting all conversation IDs");
        List<String> conversationIds = chatService.getConversationIds();
        return ResponseEntity.ok(conversationIds);
    }
    
    @GetMapping("/conversations/{conversationId}")
    public ResponseEntity<List<ChatMessage>> getConversation(@PathVariable String conversationId) {
        log.info("Getting conversation: {}", conversationId);
        List<ChatMessage> messages = chatService.getConversation(conversationId);
        return ResponseEntity.ok(messages);
    }
    
    @PostMapping("/send")
    public ResponseEntity<ChatMessage> sendMessage(@RequestBody McpRequest request) {
        log.info("Sending message to server {}: {}", request.getServerId(), request.getMessage());
        ChatMessage response = chatService.sendMessage(request);
        return ResponseEntity.ok(response);
    }
    
    @DeleteMapping("/conversations/{conversationId}")
    public ResponseEntity<Void> clearConversation(@PathVariable String conversationId) {
        log.info("Clearing conversation: {}", conversationId);
        chatService.clearConversation(conversationId);
        return ResponseEntity.ok().build();
    }
}