package org.shark.mentor.mcp.model;

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
    private String baseUrl;
    private String status;
    private Long lastConnected;
    private String lastError;
    private String protocol;
    private Boolean implemented;



    // Constructor adicional para 5 parámetros
    public McpServer(String id, String name, String description, String url, String status) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.url = url;
        this.status = status;
        this.lastConnected = null;
    }

    public boolean isRemote() {
        return protocol != null && (protocol.equalsIgnoreCase("http") || protocol.equalsIgnoreCase("https"));
    }

    public boolean isImplemented() {
        return implemented != null && implemented;
    }
}