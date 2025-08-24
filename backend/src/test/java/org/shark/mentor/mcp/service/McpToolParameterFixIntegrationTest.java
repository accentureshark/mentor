
package org.shark.mentor.mcp.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.shark.mentor.mcp.model.McpServer;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * Integration test to verify the fix for the MCP tool parameter inference issue
 * that was causing tools with no parameters to incorrectly require parameters.
 */
@ExtendWith(MockitoExtension.class)
class McpToolParameterFixIntegrationTest {

    @Mock
    private McpServerService mcpServerService;

    @Mock
    private LlmService llmService;

    private McpToolService mcpToolService;
    private McpToolOrchestrator mcpToolOrchestrator;

    @BeforeEach
    void setUp() {
        mcpToolService = new McpToolService(mcpServerService, llmService);
        mcpToolOrchestrator = new McpToolOrchestrator(mcpServerService, mcpToolService);
    }

    @Test
    void testOriginalScenarioFromLogs() {
        // Simulate the exact scenario from the problem logs
        McpServer polentaServer = new McpServer();
        polentaServer.setId("polenta-local");
        polentaServer.setName("Polenta MCP Server (Local)");
        polentaServer.setUrl("http://localhost:8090");
        polentaServer.setStatus("CONNECTED");

        // Create the exact tools from the logs
        List<Map<String, Object>> tools = createToolsFromLogs();

        // Mock the tool service to return our test tools
        McpToolService testService = new McpToolService(mcpServerService, llmService) {
            public List<Map<String, Object>> getTools(McpServer server) {
                return tools;
            }

            public Map<String, Object> getToolSchemaByName(String toolName, McpServer server) {
                return tools.stream()
                    .filter(tool -> toolName.equals(tool.get("name")))
                    .findFirst()
                    .orElse(null);
            }
        };

        // Test 1: LLM returns formatted tool name (the original issue)
        when(llmService.generate(anyString(), anyString())).thenReturn("📁 **list_tables**");

        String selectedTool = testService.selectBestTool("list_tables", polentaServer);
        assertEquals("list_tables", selectedTool, "Should extract clean tool name from formatted LLM response");

        // Test 2: Parameter extraction for list_tables (should be empty)
        Map<String, Object> listTablesArgs = testService.extractToolArguments("list_tables", "list_tables", polentaServer);
        assertTrue(listTablesArgs.isEmpty(), "list_tables should not require any parameters");

        // Test 3: Verify tool call preparation
        Map<String, Object> listTablesSchema = testService.getToolSchemaByName("list_tables", polentaServer);
        assertNotNull(listTablesSchema);

        Map<String, Object> toolCall = testService.prepareToolCall(listTablesSchema, listTablesArgs);

        // Verify the tool call structure
        assertEquals("2.0", toolCall.get("jsonrpc"));
        assertEquals("tools/call", toolCall.get("method"));
        assertNotNull(toolCall.get("id"));

        Map<String, Object> params = (Map<String, Object>) toolCall.get("params");
        assertEquals("list_tables", params.get("name"));

        Map<String, Object> arguments = (Map<String, Object>) params.get("arguments");
        assertTrue(arguments.isEmpty(), "Arguments should be empty for list_tables");

        // Test 4: Compare with query_data tool that does require parameters
        when(llmService.generate(anyString(), anyString())).thenReturn("{\"query\":\"SELECT * FROM tables\"}");
        Map<String, Object> queryDataArgs = testService.extractToolArguments("show me all tables", "query_data", polentaServer);
        assertFalse(queryDataArgs.isEmpty(), "query_data should extract parameters");
        assertTrue(queryDataArgs.containsKey("query"), "query_data should have query parameter");
    }

    @Test
    void testLlmResponseCleanupVariations() {
        McpServer server = new McpServer();
        server.setId("test");
        server.setName("Test Server");

        Map<String, Object> listTablesSchema = createListTablesSchema();
        List<Map<String, Object>> tools = Arrays.asList(listTablesSchema);

        McpToolService testService = new McpToolService(mcpServerService, llmService) {
            public List<Map<String, Object>> getTools(McpServer server) {
                return tools;
            }
        };

        // Test various problematic LLM responses that should all resolve to "list_tables"
        String[] problematicResponses = {
            "📁 **list_tables**",
            "🔍 I recommend using: list_tables",
            "The best tool for this is **list_tables**",
            "list_tables\n",
            "\"list_tables\"",
            "'list_tables'",
            "`list_tables`",
            "Tool: list_tables",
            "📊 list_tables 📊"
        };

        for (String llmResponse : problematicResponses) {
            when(llmService.generate(anyString(), anyString())).thenReturn(llmResponse);
            String result = testService.selectBestTool("list tables", server);
            assertEquals("list_tables", result,
                "Failed to extract 'list_tables' from LLM response: '" + llmResponse + "'");
        }
    }

    private List<Map<String, Object>> createToolsFromLogs() {
        List<Map<String, Object>> tools = new ArrayList<>();

        // query_data tool (has required query parameter)
        Map<String, Object> queryDataTool = new HashMap<>();
        queryDataTool.put("name", "query_data");
        queryDataTool.put("description", "Execute natural language or SQL queries against the data lake");

        Map<String, Object> queryDataInputSchema = new HashMap<>();
        queryDataInputSchema.put("type", "object");

        Map<String, Object> queryDataProperties = new HashMap<>();
        Map<String, Object> queryProperty = new HashMap<>();
        queryProperty.put("type", "string");
        queryProperty.put("description", "Natural language query or SQL statement to execute");
        queryDataProperties.put("query", queryProperty);

        queryDataInputSchema.put("properties", queryDataProperties);
        queryDataInputSchema.put("required", Arrays.asList("query"));
        queryDataTool.put("input_schema", queryDataInputSchema);

        tools.add(queryDataTool);

        // list_tables tool (no parameters - this is the one from the logs causing issues)
        Map<String, Object> listTablesTool = new HashMap<>();
        listTablesTool.put("name", "list_tables");
        listTablesTool.put("description", "List all available tables in the data lake");

        Map<String, Object> listTablesInputSchema = new HashMap<>();
        listTablesInputSchema.put("type", "object");
        listTablesInputSchema.put("properties", Collections.emptyMap()); // Empty properties!
        listTablesTool.put("input_schema", listTablesInputSchema);

        tools.add(listTablesTool);

        // Add other tools from logs for completeness
        tools.add(createSimpleTool("accessible_tables", "List tables the user has permission to query"));
        tools.add(createToolWithParameter("describe_table", "Get detailed information about a specific table structure",
                "table_name", "string", "Name of the table to describe (format: schema.table or just table)"));
        tools.add(createToolWithParameter("sample_data", "Get sample data from a specific table",
                "table_name", "string", "Name of the table to get sample data from"));
        tools.add(createToolWithParameter("search_tables", "Search for tables containing specific keywords",
                "keyword", "string", "Keyword to search for in table names"));
        tools.add(createSimpleTool("get_suggestions", "Get helpful query suggestions for users"));

        return tools;
    }

    private Map<String, Object> createListTablesSchema() {
        Map<String, Object> tool = new HashMap<>();
        tool.put("name", "list_tables");
        tool.put("description", "List all available tables in the data lake");

        Map<String, Object> inputSchema = new HashMap<>();
        inputSchema.put("type", "object");
        inputSchema.put("properties", Collections.emptyMap());
        tool.put("input_schema", inputSchema);

        return tool;
    }

    private Map<String, Object> createSimpleTool(String name, String description) {
        Map<String, Object> tool = new HashMap<>();
        tool.put("name", name);
        tool.put("description", description);

        Map<String, Object> inputSchema = new HashMap<>();
        inputSchema.put("type", "object");
        inputSchema.put("properties", Collections.emptyMap());
        tool.put("input_schema", inputSchema);

        return tool;
    }

    private Map<String, Object> createToolWithParameter(String name, String description,
                                                       String paramName, String paramType, String paramDescription) {
        Map<String, Object> tool = new HashMap<>();
        tool.put("name", name);
        tool.put("description", description);

        Map<String, Object> inputSchema = new HashMap<>();
        inputSchema.put("type", "object");

        Map<String, Object> properties = new HashMap<>();
        Map<String, Object> param = new HashMap<>();
        param.put("type", paramType);
        param.put("description", paramDescription);
        properties.put(paramName, param);

        inputSchema.put("properties", properties);
        inputSchema.put("required", Arrays.asList(paramName));
        tool.put("input_schema", inputSchema);

        return tool;
    }
}