package org.shark.mentor.mcp.model;

import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

/**
 * Represents a response from an MCP server
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class McpResponse {
    private String id;
    private String serverId;
    private String content;
    private String status;
    private Long timestamp;
    private String error;
    private Object data;
    
    public static McpResponse success(String id, String serverId, String content, Object data) {
        return McpResponse.builder()
                .id(id)
                .serverId(serverId)
                .content(content)
                .data(data)
                .status("SUCCESS")
                .timestamp(System.currentTimeMillis())
                .build();
    }
    
    public static McpResponse error(String id, String serverId, String error) {
        return McpResponse.builder()
                .id(id)
                .serverId(serverId)
                .error(error)
                .status("ERROR")
                .timestamp(System.currentTimeMillis())
                .build();
    }
}
