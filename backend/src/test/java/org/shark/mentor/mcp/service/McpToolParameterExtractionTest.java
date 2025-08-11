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
 * Test for MCP tool parameter extraction logic to ensure MCP compliance
 */
@ExtendWith(MockitoExtension.class)
class McpToolParameterExtractionTest {

    @Mock
    private McpServerService mcpServerService;

    @Mock
    private LlmService llmService;

    private McpToolService mcpToolService;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        mcpToolService = new McpToolService(mcpServerService, llmService);
        objectMapper = new ObjectMapper();
    }

    @Test
    void testListTablesToolWithNoParameters() {
        // Create tool definition for list_tables (no parameters required)
        Map<String, Object> listTablesToolSchema = createToolSchema(
            "list_tables",
            "List all available tables in the data lake",
            Collections.emptyMap() // No parameters
        );
        
        // Create a custom McpToolService that we can control
        McpToolService testService = new McpToolService(mcpServerService, llmService) {
            @Override
            public Map<String, Object> getToolSchemaByName(String toolName) {
                if ("list_tables".equals(toolName)) {
                    return listTablesToolSchema;
                }
                return null;
            }
        };
        
        // Test extracting arguments for list_tables
        Map<String, Object> extractedArgs = testService.extractToolArguments("list_tables", "list_tables");
        
        // Should return empty map since the tool has no parameters
        assertTrue(extractedArgs.isEmpty(), "list_tables tool should not require any parameters");
    }

    @Test
    void testQueryDataToolWithRequiredParameter() {
        // Create tool definition for query_data (requires query parameter)
        Map<String, Object> queryParamSchema = new HashMap<>();
        queryParamSchema.put("type", "string");
        queryParamSchema.put("description", "Natural language query or SQL statement to execute");
        
        Map<String, Object> properties = new HashMap<>();
        properties.put("query", queryParamSchema);
        
        Map<String, Object> queryDataToolSchema = createToolSchema(
            "query_data",
            "Execute natural language or SQL queries against the data lake",
            properties,
            Arrays.asList("query") // Required parameter
        );
        
        // When LLM tries to extract the query parameter
        when(llmService.generate(anyString(), anyString())).thenReturn("{\"query\":\"SELECT * FROM users\"}");
        
        // Create a custom McpToolService that we can control
        McpToolService testService = new McpToolService(mcpServerService, llmService) {
            @Override
            public Map<String, Object> getToolSchemaByName(String toolName) {
                if ("query_data".equals(toolName)) {
                    return queryDataToolSchema;
                }
                return null;
            }
        };
        
        // Test extracting arguments for query_data
        Map<String, Object> extractedArgs = testService.extractToolArguments("show me all users", "query_data");
        
        // Should contain query parameter
        assertFalse(extractedArgs.isEmpty(), "query_data tool should extract query parameter");
        assertTrue(extractedArgs.containsKey("query"), "Should contain query parameter");
    }

    @Test
    void testToolSelectionWithVariousFormattedResponses() {
        // Setup mock server
        McpServer server = new McpServer();
        server.setId("test-server");
        server.setName("Test Server");
        
        Map<String, Object> listTablesToolSchema = createToolSchema(
            "list_tables",
            "List all available tables in the data lake",
            Collections.emptyMap()
        );
        
        Map<String, Object> queryDataToolSchema = createToolSchema(
            "query_data", 
            "Execute queries",
            Collections.emptyMap()
        );
        
        List<Map<String, Object>> tools = Arrays.asList(listTablesToolSchema, queryDataToolSchema);
        
        McpToolService testService = new McpToolService(mcpServerService, llmService) {
            @Override
            public List<Map<String, Object>> getTools(McpServer server) {
                return tools;
            }
        };
        
        // Test various formatted responses
        when(llmService.generate(anyString(), anyString())).thenReturn("📁 **list_tables**");
        assertEquals("list_tables", testService.selectBestTool("list tables", server));
        
        when(llmService.generate(anyString(), anyString())).thenReturn("query_data");
        assertEquals("query_data", testService.selectBestTool("query data", server));
        
        when(llmService.generate(anyString(), anyString())).thenReturn("🔍 list_tables with formatting");
        assertEquals("list_tables", testService.selectBestTool("list tables", server));
        
        when(llmService.generate(anyString(), anyString())).thenReturn("The best tool is: **query_data**");
        assertEquals("query_data", testService.selectBestTool("query data", server));
    }

    @Test
    void testParameterExtractionForDifferentScenarios() {
        // Test required parameter extraction
        Map<String, Object> queryParamSchema = new HashMap<>();
        queryParamSchema.put("type", "string");
        queryParamSchema.put("description", "SQL query to execute");
        
        Map<String, Object> properties = new HashMap<>();
        properties.put("query", queryParamSchema);
        
        Map<String, Object> toolSchema = createToolSchema(
            "query_data",
            "Execute SQL queries",
            properties,
            Arrays.asList("query")
        );
        
        McpToolService testService = new McpToolService(mcpServerService, llmService) {
            @Override
            public Map<String, Object> getToolSchemaByName(String toolName) {
                if ("query_data".equals(toolName)) {
                    return toolSchema;
                }
                return null;
            }
        };
        
        // Test with LLM returning valid value
        when(llmService.generate(anyString(), anyString())).thenReturn("{\"query\":\"SELECT * FROM users\"}");
        Map<String, Object> args = testService.extractToolArguments("show all users", "query_data");
        assertEquals("SELECT * FROM users", args.get("query"));

        // Test with LLM returning NULL
        when(llmService.generate(anyString(), anyString())).thenReturn("{\"query\":null}");
        args = testService.extractToolArguments("show", "query_data");
        assertEquals("show", args.get("query")); // Should fallback to user message for required params
        
        // Test with empty properties (no parameters)
        Map<String, Object> noParamToolSchema = createToolSchema(
            "list_tables",
            "List tables",
            Collections.emptyMap()
        );
        
        McpToolService noParamTestService = new McpToolService(mcpServerService, llmService) {
            @Override
            public Map<String, Object> getToolSchemaByName(String toolName) {
                if ("list_tables".equals(toolName)) {
                    return noParamToolSchema;
                }
                return null;
            }
        };
        
        args = noParamTestService.extractToolArguments("list tables", "list_tables");
        assertTrue(args.isEmpty(), "Tools with no parameters should return empty arguments");
    }

    @Test
    void testPrepareToolCallWithNoParameters() {
        Map<String, Object> toolSchema = createToolSchema(
            "list_tables",
            "List all available tables in the data lake",
            Collections.emptyMap()
        );
        
        Map<String, Object> emptyArguments = Collections.emptyMap();
        
        Map<String, Object> toolCall = mcpToolService.prepareToolCall(toolSchema, emptyArguments);
        
        // Verify structure
        assertEquals("2.0", toolCall.get("jsonrpc"));
        assertEquals("tools/call", toolCall.get("method"));
        assertNotNull(toolCall.get("id"));
        
        Map<String, Object> params = (Map<String, Object>) toolCall.get("params");
        assertEquals("list_tables", params.get("name"));
        
        Map<String, Object> arguments = (Map<String, Object>) params.get("arguments");
        assertTrue(arguments.isEmpty(), "Arguments should be empty for tools with no parameters");
    }

    private Map<String, Object> createToolSchema(String name, String description, Map<String, Object> properties) {
        return createToolSchema(name, description, properties, Collections.emptyList());
    }

    private Map<String, Object> createToolSchema(String name, String description, Map<String, Object> properties, List<String> required) {
        Map<String, Object> inputSchema = new HashMap<>();
        inputSchema.put("type", "object");
        inputSchema.put("properties", properties);
        if (!required.isEmpty()) {
            inputSchema.put("required", required);
        }
        
        Map<String, Object> toolSchema = new HashMap<>();
        toolSchema.put("name", name);
        toolSchema.put("description", description);
        toolSchema.put("input_schema", inputSchema);
        
        return toolSchema;
    }
}