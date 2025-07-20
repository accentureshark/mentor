package org.shark.mentor.mcp.model;

import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

/**
 * Represents a chat message in the MCP client
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatMessage {
    private String id;
    private String role; // USER, ASSISTANT, SYSTEM
    private String content;
    private Long timestamp;
    private String serverId; // Which MCP server this message is related to
}