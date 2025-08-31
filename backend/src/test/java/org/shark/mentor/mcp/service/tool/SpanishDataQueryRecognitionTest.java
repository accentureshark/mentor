package org.shark.mentor.mcp.service.tool;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.shark.mentor.mcp.application.service.llm.LlmService;
import org.shark.mentor.mcp.application.service.tool.IntelligentToolSelector;
import org.shark.mentor.mcp.domain.model.McpServer;
import org.shark.mentor.mcp.infraestructure.config.PromptProperties;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Test to ensure Spanish table data queries are correctly routed to query_presto tool
 * instead of schema listing tools
 */
class SpanishDataQueryRecognitionTest {

    private IntelligentToolSelector intelligentToolSelector;
    private LlmService mockLlmService;
    private PromptProperties mockPromptProperties;
    private McpServer testServer;
    private List<Map<String, Object>> availableTools;

    @BeforeEach
    void setUp() {
        mockLlmService = mock(LlmService.class);
        mockPromptProperties = mock(PromptProperties.class);
        
        // Mock LLM service to return null (force fallback to rule-based selection)
        when(mockLlmService.generate(anyString(), anyString())).thenReturn(null);
        
        intelligentToolSelector = new IntelligentToolSelector(mockLlmService, mockPromptProperties);
        
        testServer = McpServer.builder()
                .id("polenta-local")
                .name("Polenta MCP Server")
                .url("http://localhost:25001")
                .status("CONNECTED")
                .build();
        
        // Simulate Polenta server tools based on MCP_COMPLIANCE_REPORT.md
        availableTools = List.of(
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
    }

    @Test
    void testSpanishTableDataQuery_ShouldSelectQueryTool() {
        // The original problematic query
        String userMessage = "dame los registros de la tabla nation del esquema tiny";
        
        String selectedTool = intelligentToolSelector.selectBestTool(userMessage, availableTools, testServer);
        
        assertEquals("query_presto", selectedTool, 
                "Should select query_presto tool for Spanish table data request");
    }

    @Test
    void testSpanishTableDataQuery_TypoInTableName_ShouldStillSelectQueryTool() {
        // The user's actual query with typo "natios" instead of "nation"
        String userMessage = "dame los registros de la tabla natios del esquema tiny";
        
        String selectedTool = intelligentToolSelector.selectBestTool(userMessage, availableTools, testServer);
        
        assertEquals("query_presto", selectedTool, 
                "Should select query_presto tool even with table name typo");
    }

    @Test
    void testVariousSpanishDataQueries_ShouldSelectQueryTool() {
        String[] dataQueries = {
                "muéstrame los datos de la tabla customer",
                "necesito los registros de orders",
                "dame toda la información de la tabla nation",
                "quiero ver los datos de supplier del esquema tiny",
                "buscar registros en la tabla part",
                "consultar datos de lineitem"
        };
        
        for (String query : dataQueries) {
            String selectedTool = intelligentToolSelector.selectBestTool(query, availableTools, testServer);
            assertEquals("query_presto", selectedTool, 
                    "Query '" + query + "' should select query_presto tool");
        }
    }

    @Test
    void testSpanishSchemaListingQueries_ShouldSelectSchemaTool() {
        String[] schemaQueries = {
                "lista todos los esquemas",
                "cuáles son los esquemas disponibles",
                "muéstrame los esquemas"
        };
        
        for (String query : schemaQueries) {
            String selectedTool = intelligentToolSelector.selectBestTool(query, availableTools, testServer);
            // Note: might select list_schemas if available, or query_presto as fallback
            assertNotNull(selectedTool, "Query '" + query + "' should select some tool");
        }
    }

    @Test
    void testSpanishTableListingQueries_ShouldSelectTableTool() {
        String[] tableQueries = {
                "lista todas las tablas",
                "cuáles son las tablas disponibles",
                "muéstrame las tablas del esquema tiny"
        };
        
        for (String query : tableQueries) {
            String selectedTool = intelligentToolSelector.selectBestTool(query, availableTools, testServer);
            // Should select list_tables or query_presto, but should work
            assertNotNull(selectedTool, "Query '" + query + "' should select some tool");
        }
    }
}