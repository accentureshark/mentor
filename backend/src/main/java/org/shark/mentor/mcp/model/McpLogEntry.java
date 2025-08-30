package org.shark.mentor.mcp.model;

import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

/**
 * Represents an MCP Log Entry
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class McpLogEntry {
    private String level;
    private String message;
    private String data;
    private Long timestamp;
    private String serverId;
}