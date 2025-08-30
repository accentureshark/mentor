package org.shark.mentor.mcp.model;

import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

/**
 * Represents an MCP Notification from server to client
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class McpNotification {
    private String method;
    private Object params;
    private Long timestamp;
    private String serverId;
}