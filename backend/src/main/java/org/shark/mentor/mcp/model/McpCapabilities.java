package org.shark.mentor.mcp.model;

import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

/**
 * Represents MCP Server Capabilities
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class McpCapabilities {
    private Boolean resources;
    private Boolean tools;
    private Boolean prompts;
    private Boolean logging;
    private Boolean sampling;
    private Object experimental;
}