package org.shark.mentor.mcp.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class LlmPlannerTest {

    @Mock
    private McpServerService mcpServerService;
    @Mock
    private McpToolService mcpToolService;

    private McpToolOrchestrator orchestrator;

    @BeforeEach
    void setUp() {
        orchestrator = new McpToolOrchestrator(mcpServerService, mcpToolService);
    }

    @Test
    void testPrepareToolCallFiltersArgumentsBySchema() {
        Map<String, Object> toolSchema = new HashMap<>();
        toolSchema.put("name", "search_repositories");
        Map<String, Object> properties = Map.of(
                "q", Map.of("type", "string")
        );
        Map<String, Object> inputSchema = Map.of(
                "type", "object",
                "properties", properties
        );
        toolSchema.put("inputSchema", inputSchema);

        Map<String, Object> arguments = new HashMap<>();
        arguments.put("q", "langchain4j");
        arguments.put("extra", "value");

        Map<String, Object> call = orchestrator.prepareToolCall(toolSchema, arguments);
        Map<String, Object> params = (Map<String, Object>) call.get("params");
        Map<String, Object> args = (Map<String, Object>) params.get("arguments");

        assertEquals(1, args.size());
        assertEquals("langchain4j", args.get("q"));
        assertFalse(args.containsKey("extra"));
    }

    @Test
    void testPrepareToolCallIncludesRequiredArguments() {
        Map<String, Object> toolSchema = new HashMap<>();
        toolSchema.put("name", "get_file_contents");
        Map<String, Object> properties = Map.of(
                "owner", Map.of("type", "string"),
                "repo", Map.of("type", "string")
        );
        Map<String, Object> inputSchema = new HashMap<>();
        inputSchema.put("type", "object");
        inputSchema.put("properties", properties);
        inputSchema.put("required", List.of("owner", "repo"));
        toolSchema.put("inputSchema", inputSchema);

        Map<String, Object> arguments = new HashMap<>();
        arguments.put("owner", "octocat");
        arguments.put("repo", "hello-world");
        arguments.put("extra", "ignored");

        Map<String, Object> call = orchestrator.prepareToolCall(toolSchema, arguments);
        Map<String, Object> params = (Map<String, Object>) call.get("params");
        Map<String, Object> args = (Map<String, Object>) params.get("arguments");

        assertEquals("octocat", args.get("owner"));
        assertEquals("hello-world", args.get("repo"));
        assertFalse(args.containsKey("extra"));
    }
}
