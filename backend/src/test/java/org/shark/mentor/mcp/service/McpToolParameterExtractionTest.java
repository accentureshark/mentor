package org.shark.mentor.mcp.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.shark.mentor.mcp.model.McpServer;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class McpToolParameterExtractionTest {

    @Mock
    private McpServerService mcpServerService;

    private McpServer testServer;

    @BeforeEach
    void setUp() {
        testServer = new McpServer();
        testServer.setId("test");
        testServer.setName("Test Server");
        testServer.setUrl("http://localhost:8090");
        testServer.setStatus("CONNECTED");
    }

    @Test
    void testExtractArgumentsForToolWithRequiredParameter() {
        List<Map<String, Object>> tools = new ArrayList<>();
        tools.add(createToolWithParameter("query_data", "query", "string", "Query to execute"));

        McpToolService testService = new McpToolService(mcpServerService) {
            @Override
            public List<Map<String, Object>> getTools(McpServer server) {
                return tools;
            }
        };

        Map<String, Object> userArgs = Map.of("query", "SELECT * FROM users");
        Map<String, Object> args = filterArgumentsBySchema(userArgs, tools.get(0));
        assertEquals(1, args.size());
        assertEquals("SELECT * FROM users", args.get("query"));
    }

    @Test
    void testExtractArgumentsForToolWithNoParameters() {
        List<Map<String, Object>> tools = new ArrayList<>();
        tools.add(createToolWithNoParameters("list_tables"));

        McpToolService testService = new McpToolService(mcpServerService) {
            @Override
            public List<Map<String, Object>> getTools(McpServer server) {
                return tools;
            }
        };

        Map<String, Object> userArgs = Map.of();
        Map<String, Object> args = filterArgumentsBySchema(userArgs, tools.get(0));
        assertTrue(args.isEmpty());
    }

    @Test
    void testExtractArgumentsWithExtraFieldsIgnored() {
        List<Map<String, Object>> tools = new ArrayList<>();
        tools.add(createToolWithParameter("describe_table", "table_name", "string", "Table to describe"));

        McpToolService testService = new McpToolService(mcpServerService) {
            @Override
            public List<Map<String, Object>> getTools(McpServer server) {
                return tools;
            }
        };

        Map<String, Object> userArgs = Map.of("table_name", "users", "extra", "ignored");
        Map<String, Object> args = filterArgumentsBySchema(userArgs, tools.get(0));
        assertEquals(1, args.size());
        assertEquals("users", args.get("table_name"));
        assertFalse(args.containsKey("extra"));
    }

    @Test
    void testExtractArgumentsReturnsEmptyForUnknownTool() {
        List<Map<String, Object>> tools = new ArrayList<>();
        McpToolService testService = new McpToolService(mcpServerService) {
            @Override
            public List<Map<String, Object>> getTools(McpServer server) {
                return tools;
            }
        };

        Map<String, Object> userArgs = Map.of();
        // No tool schema, so should return empty
        Map<String, Object> args = filterArgumentsBySchema(userArgs, null);
        assertTrue(args.isEmpty());
    }

    private Map<String, Object> filterArgumentsBySchema(Map<String, Object> userArgs, Map<String, Object> tool) {
        if (tool == null) return Collections.emptyMap();
        Map<String, Object> inputSchema = (Map<String, Object>) tool.get("input_schema");
        if (inputSchema == null) return Collections.emptyMap();
        Map<String, Object> properties = (Map<String, Object>) inputSchema.get("properties");
        if (properties == null) return Collections.emptyMap();
        Map<String, Object> filtered = new HashMap<>();
        for (String key : userArgs.keySet()) {
            if (properties.containsKey(key)) {
                filtered.put(key, userArgs.get(key));
            }
        }
        return filtered;
    }

    private Map<String, Object> createToolWithParameter(String name, String paramName, String paramType, String paramDescription) {
        Map<String, Object> tool = new HashMap<>();
        tool.put("name", name);
        tool.put("description", "desc");

        Map<String, Object> inputSchema = new HashMap<>();
        inputSchema.put("type", "object");

        Map<String, Object> properties = new HashMap<>();
        Map<String, Object> param = new HashMap<>();
        param.put("type", paramType);
        param.put("description", paramDescription);
        properties.put(paramName, param);

        inputSchema.put("properties", properties);
        inputSchema.put("required", Collections.singletonList(paramName));
        tool.put("input_schema", inputSchema);

        return tool;
    }

    private Map<String, Object> createToolWithNoParameters(String name) {
        Map<String, Object> tool = new HashMap<>();
        tool.put("name", name);
        tool.put("description", "desc");
        Map<String, Object> inputSchema = new HashMap<>();
        inputSchema.put("type", "object");
        inputSchema.put("properties", new HashMap<>());
        inputSchema.put("required", new ArrayList<>());
        tool.put("input_schema", inputSchema);
        return tool;
    }
}
