package org.shark.alma.mcp.model;

import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

/**
 * Represents a request to send a message to an MCP server
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class McpRequest {
    private String serverId;
    private String message;
    private String conversationId;
}