package org.shark.mentor.mcp.interfaces.rest;

import org.shark.mentor.mcp.application.service.chat.ChatService;
import org.shark.mentor.mcp.application.service.llm.StreamingLlmService;
import org.shark.mentor.mcp.domain.model.ChatMessage;
import org.shark.mentor.mcp.domain.model.McpRequest;

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
    private final StreamingLlmService streamingLlmService;
    
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
    
    @PostMapping(value = "/stream", produces = "text/event-stream")
    public SseEmitter streamMessage(@RequestBody McpRequest request) {
        log.info("Streaming message to server {}: {}", request.getServerId(), request.getMessage());
        
        String conversationId = request.getConversationId();
        if (conversationId == null) {
            conversationId = "default";
        }
        
        // For now, we'll use the streaming service directly
        // In the future, we could integrate this with ChatService for MCP tool orchestration
        return streamingLlmService.generateStreamingWithMemory(
            conversationId,
            request.getMessage(),
            "Context from MCP server would be here" // TODO: Integrate with MCP tool orchestration
        );
    }
    
    @DeleteMapping("/conversations/{conversationId}")
    public ResponseEntity<Void> clearConversation(@PathVariable String conversationId) {
        log.info("Clearing conversation: {}", conversationId);
        chatService.clearConversation(conversationId);
        return ResponseEntity.ok().build();
    }
}