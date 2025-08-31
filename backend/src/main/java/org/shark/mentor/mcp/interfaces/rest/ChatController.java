package org.shark.mentor.mcp.interfaces.rest;

import org.shark.mentor.mcp.application.service.chat.ChatService;
import org.shark.mentor.mcp.domain.model.ChatMessage;
import org.shark.mentor.mcp.domain.model.McpRequest;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
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
    
    @PostMapping(value = "/send/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter sendMessageStream(@RequestBody McpRequest request) {
        log.info("Sending streaming message to server {}: {}", request.getServerId(), request.getMessage());
        return chatService.sendMessageStream(request);
    }
    
    @DeleteMapping("/conversations/{conversationId}")
    public ResponseEntity<Void> clearConversation(@PathVariable String conversationId) {
        log.info("Clearing conversation: {}", conversationId);
        chatService.clearConversation(conversationId);
        return ResponseEntity.ok().build();
    }
}