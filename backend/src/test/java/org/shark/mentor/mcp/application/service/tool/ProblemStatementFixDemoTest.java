package org.shark.mentor.mcp.application.service.tool;

import org.junit.jupiter.api.Test;
import org.shark.mentor.mcp.application.service.llm.LlmService;
import org.shark.mentor.mcp.domain.model.McpServer;
import org.shark.mentor.mcp.infraestructure.config.PromptProperties;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

/**
 * Integration test demonstrating the fix for the specific problem statement
 */
public class ProblemStatementFixDemoTest {

    @Test
    public void demonstrateSpanishTableQueryFix() {
        // Setup
        LlmService mockLlmService = mock(LlmService.class);
        PromptProperties mockPromptProperties = mock(PromptProperties.class);
        
        // Mock LLM service to return null (force fallback to rule-based selection)
        when(mockLlmService.generate(anyString(), anyString())).thenReturn(null);
        
        IntelligentToolSelector selector = new IntelligentToolSelector(mockLlmService, mockPromptProperties);
        
        McpServer polentaServer = McpServer.builder()
                .id("polenta-local")
                .name("Polenta MCP Server (Local)")
                .url("http://localhost:25001")
                .status("CONNECTED")
                .build();
        
        // Available tools from the Polenta MCP server (as described in MCP_COMPLIANCE_REPORT.md)
        List<Map<String, Object>> availableTools = List.of(
                Map.of(
                        "name", "query_presto",
                        "description", "Execute a query against the PrestoDB data lake"
                ),
                Map.of(
                        "name", "list_tables", 
                        "description", "List available tables in data lake"
                ),
                Map.of(
                        "name", "describe_table",
                        "description", "Get table schema and metadata"
                ),
                Map.of(
                        "name", "list_schemas",
                        "description", "List available schemas"
                )
        );
        
        // The EXACT problematic query from the problem statement
        String problematicQuery = "dame los registros de la tabla natios del esquema tiny";
        
        // Test the fix
        String selectedTool = selector.selectBestTool(problematicQuery, availableTools, polentaServer);
        
        // Verify it now routes to the correct tool
        assertEquals("query_presto", selectedTool, 
                "The Spanish table data query should now route to query_presto instead of schema listing tools");
        
        // Additional verification: test the corrected query too
        String correctedQuery = "dame los registros de la tabla nation del esquema tiny";
        String selectedToolCorrected = selector.selectBestTool(correctedQuery, availableTools, polentaServer);
        
        assertEquals("query_presto", selectedToolCorrected,
                "The corrected Spanish table data query should also route to query_presto");
    }
}