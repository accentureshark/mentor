package org.shark.mentor.mcp.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class McpToolSchedulerTest {

    @Mock
    private McpServerService mcpServerService;

    @Mock
    private LlmService llmService;

    private McpToolService mcpToolService;

    @BeforeEach
    void setUp() {
        mcpToolService = new McpToolService(mcpServerService, llmService);
    }

    @Test
    void scheduleToolCallProducesValidJsonRpc() throws Exception {
        Map<String, Object> inputSchema = Map.of(
                "type", "object",
                "properties", Map.of("q", Map.of("type", "string")),
                "required", List.of("q")
        );
        List<Map<String, Object>> tools = List.of(
                Map.of("name", "search_repositories", "inputSchema", inputSchema)
        );

        String llmResponse = """
        {
          \"jsonrpc\": \"2.0\",
          \"id\": \"1\",
          \"method\": \"tools/call\",
          \"params\": {\"name\": \"search_repositories\", \"arguments\": {\"q\": \"java\"}}
        }
        """;
        when(llmService.generate(anyString(), anyString())).thenReturn(llmResponse);

        Map<String, Object> call = mcpToolService.scheduleToolCall("find java repos", tools);

        assertNotNull(call);
        assertEquals("2.0", call.get("jsonrpc"));
        assertEquals("tools/call", call.get("method"));
        Map<String, Object> params = (Map<String, Object>) call.get("params");
        assertEquals("search_repositories", params.get("name"));
        Map<String, Object> args = (Map<String, Object>) params.get("arguments");
        assertEquals("java", args.get("q"));

        List<String> required = (List<String>) inputSchema.get("required");
        for (String req : required) {
            assertTrue(args.containsKey(req));
        }
    }
}
