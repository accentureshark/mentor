package org.shark.mentor.mcp.model;

import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

/**
 * Represents an MCP Prompt Template
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class McpPrompt {
    private String name;
    private String description;
    private Object arguments;
}