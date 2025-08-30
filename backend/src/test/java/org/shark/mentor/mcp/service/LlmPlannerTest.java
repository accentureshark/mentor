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
    void testArgumentFilteringBySchema() {
        // Simula la lógica de filtrado de argumentos por schema
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

        // Simula el filtrado manual
        Map<String, Object> filteredArgs = new HashMap<>();
        Map<String, Object> props = (Map<String, Object>) ((Map<String, Object>) toolSchema.get("inputSchema")).get("properties");
        for (String key : arguments.keySet()) {
            if (props.containsKey(key)) {
                filteredArgs.put(key, arguments.get(key));
            }
        }
        assertEquals(1, filteredArgs.size());
        assertEquals("langchain4j", filteredArgs.get("q"));
        assertFalse(filteredArgs.containsKey("extra"));
    }

    @Test
    void testArgumentFilteringBySchemaForRequiredArguments() {
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

        // Filtrado manual de argumentos según el schema
        Map<String, Object> filteredArgs = new HashMap<>();
        Map<String, Object> props = (Map<String, Object>) ((Map<String, Object>) toolSchema.get("inputSchema")).get("properties");
        for (String key : arguments.keySet()) {
            if (props.containsKey(key)) {
                filteredArgs.put(key, arguments.get(key));
            }
        }
        assertEquals("octocat", filteredArgs.get("owner"));
        assertEquals("hello-world", filteredArgs.get("repo"));
        assertFalse(filteredArgs.containsKey("extra"));
    }
}
