package org.shark.mentor.mcp.model;

import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.util.UUID;

/**
 * Represents a chat message in the MCP client
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatMessage {

    private String id;
    private String role;
    private String content;
    private long timestamp;
    private String serverId;

    public static ChatMessage system(String content) {
        ChatMessage message = new ChatMessage();
        message.id = UUID.randomUUID().toString();
        message.role = "SYSTEM";
        message.content = content;
        message.timestamp = System.currentTimeMillis();
        return message;
    }
}