package org.shark.alma.mcp.model;

import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

/**
 * Represents an MCP Server registration
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class McpServer {
    private String id;
    private String name;
    private String description;
    private String url;
    private String status; // CONNECTED, DISCONNECTED, ERROR
    private Long lastConnected;
}