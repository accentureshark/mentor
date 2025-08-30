package org.shark.mentor.mcp.model;

import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

/**
 * Represents an MCP Resource - static data or files that can be accessed
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class McpResource {
    private String uri;
    private String name;
    private String description;
    private String mimeType;
    private Object annotations;
}