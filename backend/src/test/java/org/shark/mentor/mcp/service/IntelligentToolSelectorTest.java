package org.shark.mentor.mcp.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.shark.mentor.mcp.model.McpServer;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IntelligentToolSelectorTest {

    @Mock
    private LlmService llmService;

    private IntelligentToolSelector intelligentToolSelector;
    private McpServer testServer;
    private List<Map<String, Object>> dataLakeTools;

    @BeforeEach
    void setUp() {
        intelligentToolSelector = new IntelligentToolSelector(llmService);
        testServer = new McpServer("test-server", "Data Lake Server", "Data Lake MCP Server", "http://localhost:8080", "active");
        
        // Setup data lake tools similar to the problem statement
        dataLakeTools = List.of(
            Map.of("name", "query_data", "description", "Ejecuta una consulta en lenguaje natural o SQL sobre el data lake",
                   "inputSchema", Map.of("properties", Map.of("query", Map.of("type", "string")))),
            Map.of("name", "list_tables", "description", "Lista todas las tablas disponibles en el data lake",
                   "inputSchema", Map.of("properties", Map.of())),
            Map.of("name", "describe_table", "description", "Devuelve la estructura de una tabla específica",
                   "inputSchema", Map.of("properties", Map.of("table", Map.of("type", "string")))),
            Map.of("name", "sample_data", "description", "Devuelve datos de ejemplo de una tabla específica",
                   "inputSchema", Map.of("properties", Map.of("table", Map.of("type", "string"), "limit", Map.of("type", "integer")))),
            Map.of("name", "search_tables", "description", "Busca tablas que contengan una palabra clave",
                   "inputSchema", Map.of("properties", Map.of("keyword", Map.of("type", "string"))))
        );
    }

    @Test
    void shouldSelectQueryDataForNaturalLanguageQuery() {
        // Given
        String userMessage = "Dame las ventas del último mes";
        when(llmService.generate(anyString(), contains("tool selector"))).thenReturn("query_data");

        // When
        String selectedTool = intelligentToolSelector.selectBestTool(userMessage, dataLakeTools, testServer);

        // Then
        assertEquals("query_data", selectedTool);
    }

    @Test
    void shouldSelectListTablesForTableListingRequest() {
        // Given
        String userMessage = "¿Qué tablas hay disponibles?";
        when(llmService.generate(anyString(), contains("tool selector"))).thenReturn("list_tables");

        // When
        String selectedTool = intelligentToolSelector.selectBestTool(userMessage, dataLakeTools, testServer);

        // Then
        assertEquals("list_tables", selectedTool);
    }

    @Test
    void shouldSelectDescribeTableForTableStructureRequest() {
        // Given
        String userMessage = "Muéstrame la estructura de la tabla clientes";
        when(llmService.generate(anyString(), contains("tool selector"))).thenReturn("describe_table");

        // When
        String selectedTool = intelligentToolSelector.selectBestTool(userMessage, dataLakeTools, testServer);

        // Then
        assertEquals("describe_table", selectedTool);
    }

    @Test
    void shouldSelectSampleDataForExampleDataRequest() {
        // Given
        String userMessage = "Dame 10 filas de la tabla productos";
        when(llmService.generate(anyString(), contains("tool selector"))).thenReturn("sample_data");

        // When
        String selectedTool = intelligentToolSelector.selectBestTool(userMessage, dataLakeTools, testServer);

        // Then
        assertEquals("sample_data", selectedTool);
    }

    @Test
    void shouldSelectSearchTablesForTableSearchRequest() {
        // Given
        String userMessage = "Buscar tablas relacionadas con ventas";
        when(llmService.generate(anyString(), contains("tool selector"))).thenReturn("search_tables");

        // When
        String selectedTool = intelligentToolSelector.selectBestTool(userMessage, dataLakeTools, testServer);

        // Then
        assertEquals("search_tables", selectedTool);
    }

    @Test
    void shouldExtractQueryArgumentForNaturalLanguageQuery() {
        // Given
        String userMessage = "Dame las ventas del último mes";
        String toolName = "query_data";
        Map<String, Object> toolSchema = Map.of("inputSchema", 
            Map.of("properties", Map.of("query", Map.of("type", "string"))));
        
        when(llmService.generate(anyString(), contains("parameter extractor")))
            .thenReturn("{\"query\": \"ventas del último mes\"}");

        // When
        Map<String, Object> arguments = intelligentToolSelector.extractToolArguments(
            userMessage, toolName, toolSchema, testServer);

        // Then
        assertEquals("ventas del último mes", arguments.get("query"));
    }

    @Test
    void shouldExtractTableArgumentForDescribeTableRequest() {
        // Given
        String userMessage = "Describe la tabla clientes";
        String toolName = "describe_table";
        Map<String, Object> toolSchema = Map.of("inputSchema",
            Map.of("properties", Map.of("table", Map.of("type", "string"))));
        
        when(llmService.generate(anyString(), contains("parameter extractor")))
            .thenReturn("{\"table\": \"clientes\"}");

        // When
        Map<String, Object> arguments = intelligentToolSelector.extractToolArguments(
            userMessage, toolName, toolSchema, testServer);

        // Then
        assertEquals("clientes", arguments.get("table"));
    }

    @Test
    void shouldExtractMultipleArgumentsForSampleDataRequest() {
        // Given
        String userMessage = "Dame 5 filas de usuarios";
        String toolName = "sample_data";
        Map<String, Object> toolSchema = Map.of("inputSchema",
            Map.of("properties", Map.of(
                "table", Map.of("type", "string"),
                "limit", Map.of("type", "integer"))));
        
        when(llmService.generate(anyString(), contains("parameter extractor")))
            .thenReturn("{\"table\": \"usuarios\", \"limit\": 5}");

        // When
        Map<String, Object> arguments = intelligentToolSelector.extractToolArguments(
            userMessage, toolName, toolSchema, testServer);

        // Then
        assertEquals("usuarios", arguments.get("table"));
        assertEquals(5, arguments.get("limit"));
    }

    @Test
    void shouldFallbackToSimpleSelectionWhenLlmFails() {
        // Given
        String userMessage = "list_tables command";
        when(llmService.generate(anyString(), anyString())).thenThrow(new RuntimeException("LLM error"));

        // When
        String selectedTool = intelligentToolSelector.selectBestTool(userMessage, dataLakeTools, testServer);

        // Then
        assertEquals("list_tables", selectedTool); // Should find by name matching
    }

    @Test
    void shouldReturnEmptyArgsWhenExtractionFails() {
        // Given
        String userMessage = "test message";
        String toolName = "test_tool";
        Map<String, Object> toolSchema = Map.of();
        when(llmService.generate(anyString(), anyString())).thenThrow(new RuntimeException("LLM error"));

        // When
        Map<String, Object> arguments = intelligentToolSelector.extractToolArguments(
            userMessage, toolName, toolSchema, testServer);

        // Then
        assertTrue(arguments.isEmpty());
    }

    @Test
    void shouldReturnNullWhenNoToolsAvailable() {
        // Given
        String userMessage = "any message";
        List<Map<String, Object>> emptyTools = List.of();

        // When
        String selectedTool = intelligentToolSelector.selectBestTool(userMessage, emptyTools, testServer);

        // Then
        assertNull(selectedTool);
    }
}