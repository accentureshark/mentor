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
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

/**
 * Integration test demonstrating the enhanced MCP client working with data lake tooling
 * as described in the problem statement
 */
@ExtendWith(MockitoExtension.class)
class DataLakeToolingIntegrationTest {

    @Mock
    private McpServerService mcpServerService;
    @Mock
    private McpToolService mcpToolService;
    @Mock
    private IntelligentToolSelector intelligentToolSelector;

    private McpToolOrchestrator orchestrator;
    private McpServer dataLakeServer;
    private List<Map<String, Object>> dataLakeTools;

    @BeforeEach
    void setUp() {
        orchestrator = new McpToolOrchestrator(mcpServerService, mcpToolService, intelligentToolSelector);
        dataLakeServer = new McpServer("datalake-1", "Data Lake Server", "Production Data Lake", "http://localhost:8080", "active");
        
        // Setup data lake tools as described in the problem statement
        dataLakeTools = List.of(
            Map.of("name", "query_data", 
                   "description", "Ejecuta una consulta en lenguaje natural o SQL sobre el data lake. Ejemplo: 'Dame las ventas del último mes' o 'SELECT * FROM ventas LIMIT 10'",
                   "inputSchema", Map.of("properties", Map.of("query", Map.of("type", "string")))),
            Map.of("name", "list_tables", 
                   "description", "Lista todas las tablas disponibles en el data lake",
                   "inputSchema", Map.of("properties", Map.of())),
            Map.of("name", "describe_table", 
                   "description", "Devuelve la estructura de una tabla específica. Ejemplo: 'Describe la tabla clientes' o '¿Qué columnas tiene ventas?'",
                   "inputSchema", Map.of("properties", Map.of("table", Map.of("type", "string")))),
            Map.of("name", "sample_data", 
                   "description", "Devuelve datos de ejemplo de una tabla específica. Ejemplo: 'Dame 10 filas de clientes'",
                   "inputSchema", Map.of("properties", Map.of(
                       "table", Map.of("type", "string"),
                       "limit", Map.of("type", "integer", "default", 10)))),
            Map.of("name", "search_tables", 
                   "description", "Busca tablas que contengan una palabra clave. Ejemplo: 'Buscar tablas con ventas'",
                   "inputSchema", Map.of("properties", Map.of("keyword", Map.of("type", "string")))),
            Map.of("name", "get_suggestions", 
                   "description", "Obtiene sugerencias de consultas útiles para el usuario",
                   "inputSchema", Map.of("properties", Map.of())),
            Map.of("name", "list_schemas", 
                   "description", "Lista todos los esquemas disponibles en la base de datos",
                   "inputSchema", Map.of("properties", Map.of())),
            Map.of("name", "count_rows", 
                   "description", "Devuelve la cantidad de filas de una tabla",
                   "inputSchema", Map.of("properties", Map.of("table", Map.of("type", "string")))),
            Map.of("name", "distinct_values", 
                   "description", "Devuelve los valores únicos de una columna específica de una tabla",
                   "inputSchema", Map.of("properties", Map.of(
                       "table", Map.of("type", "string"),
                       "column", Map.of("type", "string")))),
            Map.of("name", "search_by_column", 
                   "description", "Busca registros en una tabla donde una columna contiene un valor específico",
                   "inputSchema", Map.of("properties", Map.of(
                       "table", Map.of("type", "string"),
                       "column", Map.of("type", "string"),
                       "value", Map.of("type", "string")))),
            Map.of("name", "aggregate_query", 
                   "description", "Realiza agregaciones como suma, promedio, máximo, mínimo sobre una columna",
                   "inputSchema", Map.of("properties", Map.of(
                       "table", Map.of("type", "string"),
                       "column", Map.of("type", "string"),
                       "function", Map.of("type", "string", "enum", List.of("sum", "avg", "max", "min", "count"))))),
            Map.of("name", "group_by_query", 
                   "description", "Realiza agrupamientos y agregaciones por una columna",
                   "inputSchema", Map.of("properties", Map.of(
                       "table", Map.of("type", "string"),
                       "group_column", Map.of("type", "string"),
                       "agg_column", Map.of("type", "string"),
                       "agg_function", Map.of("type", "string")))),
            Map.of("name", "filtered_query", 
                   "description", "Permite aplicar filtros complejos sobre una tabla",
                   "inputSchema", Map.of("properties", Map.of(
                       "table", Map.of("type", "string"),
                       "filters", Map.of("type", "object")))),
            Map.of("name", "nl_to_sql", 
                   "description", "Traduce una consulta en lenguaje natural a SQL",
                   "inputSchema", Map.of("properties", Map.of("natural_query", Map.of("type", "string"))))
        );
    }

    @Test
    void shouldHandleNaturalLanguageDataQuery() {
        // Given: User wants sales data from last month in natural language
        String userMessage = "Dame las ventas del último mes";
        setupMocksForToolExecution("query_data", Map.of("query", "ventas del último mes"));
        
        // When
        String result = orchestrator.executeTool(dataLakeServer, userMessage);

        // Then
        assertEquals("Mock tool execution: query_data with {query=ventas del último mes}", result);
    }

    @Test
    void shouldHandleSqlDataQuery() {
        // Given: User provides direct SQL query
        String userMessage = "SELECT * FROM ventas WHERE fecha >= '2024-01-01' LIMIT 10";
        setupMocksForToolExecution("query_data", Map.of("query", "SELECT * FROM ventas WHERE fecha >= '2024-01-01' LIMIT 10"));
        
        // When
        String result = orchestrator.executeTool(dataLakeServer, userMessage);

        // Then
        assertEquals("Mock tool execution: query_data with {query=SELECT * FROM ventas WHERE fecha >= '2024-01-01' LIMIT 10}", result);
    }

    @Test
    void shouldHandleTableListingRequest() {
        // Given: User wants to see available tables
        String userMessage = "¿Qué tablas hay disponibles?";
        setupMocksForToolExecution("list_tables", Map.of());
        
        // When
        String result = orchestrator.executeTool(dataLakeServer, userMessage);

        // Then
        assertEquals("Mock tool execution: list_tables with {}", result);
    }

    @Test
    void shouldHandleTableDescriptionRequest() {
        // Given: User wants to see table structure
        String userMessage = "Describe la tabla clientes, necesito saber qué columnas tiene";
        setupMocksForToolExecution("describe_table", Map.of("table", "clientes"));
        
        // When
        String result = orchestrator.executeTool(dataLakeServer, userMessage);

        // Then
        assertEquals("Mock tool execution: describe_table with {table=clientes}", result);
    }

    @Test
    void shouldHandleSampleDataRequest() {
        // Given: User wants sample data with specific limit
        String userMessage = "Dame 5 filas de ejemplo de la tabla productos";
        setupMocksForToolExecution("sample_data", Map.of("table", "productos", "limit", 5));
        
        // When
        String result = orchestrator.executeTool(dataLakeServer, userMessage);

        // Then
        assertTrue(result.contains("sample_data"));
        assertTrue(result.contains("table=productos"));
        assertTrue(result.contains("limit=5"));
    }

    @Test
    void shouldHandleTableSearchRequest() {
        // Given: User wants to find tables related to a topic
        String userMessage = "Buscar tablas relacionadas con ventas";
        setupMocksForToolExecution("search_tables", Map.of("keyword", "ventas"));
        
        // When
        String result = orchestrator.executeTool(dataLakeServer, userMessage);

        // Then
        assertEquals("Mock tool execution: search_tables with {keyword=ventas}", result);
    }

    @Test
    void shouldHandleRowCountRequest() {
        // Given: User wants to know how many rows a table has
        String userMessage = "¿Cuántas filas tiene la tabla usuarios?";
        setupMocksForToolExecution("count_rows", Map.of("table", "usuarios"));
        
        // When
        String result = orchestrator.executeTool(dataLakeServer, userMessage);

        // Then
        assertEquals("Mock tool execution: count_rows with {table=usuarios}", result);
    }

    @Test
    void shouldHandleDistinctValuesRequest() {
        // Given: User wants unique values from a column
        String userMessage = "Dame los valores únicos de la columna categoria en la tabla productos";
        setupMocksForToolExecution("distinct_values", Map.of("table", "productos", "column", "categoria"));
        
        // When
        String result = orchestrator.executeTool(dataLakeServer, userMessage);

        // Then
        assertTrue(result.contains("distinct_values"));
        assertTrue(result.contains("table=productos"));
        assertTrue(result.contains("column=categoria"));
    }

    @Test
    void shouldHandleColumnSearchRequest() {
        // Given: User wants to search for specific values in a column
        String userMessage = "Buscar en la tabla clientes donde el estado sea 'activo'";
        setupMocksForToolExecution("search_by_column", Map.of("table", "clientes", "column", "estado", "value", "activo"));
        
        // When
        String result = orchestrator.executeTool(dataLakeServer, userMessage);

        // Then
        assertTrue(result.contains("search_by_column"));
        assertTrue(result.contains("table=clientes"));
        assertTrue(result.contains("column=estado"));
        assertTrue(result.contains("value=activo"));
    }

    @Test
    void shouldHandleAggregationRequest() {
        // Given: User wants aggregated data
        String userMessage = "Dame la suma total de la columna monto en la tabla ventas";
        setupMocksForToolExecution("aggregate_query", Map.of("table", "ventas", "column", "monto", "function", "sum"));
        
        // When
        String result = orchestrator.executeTool(dataLakeServer, userMessage);

        // Then
        assertTrue(result.contains("aggregate_query"));
        assertTrue(result.contains("table=ventas"));
        assertTrue(result.contains("column=monto"));
        assertTrue(result.contains("function=sum"));
    }

    @Test
    void shouldHandleNaturalLanguageToSqlTranslation() {
        // Given: User wants to translate natural language to SQL
        String userMessage = "Traduce a SQL: clientes que compraron más de 1000 pesos el año pasado";
        setupMocksForToolExecution("nl_to_sql", Map.of("natural_query", "clientes que compraron más de 1000 pesos el año pasado"));
        
        // When
        String result = orchestrator.executeTool(dataLakeServer, userMessage);

        // Then
        assertEquals("Mock tool execution: nl_to_sql with {natural_query=clientes que compraron más de 1000 pesos el año pasado}", result);
    }

    private void setupMocksForToolExecution(String expectedTool, Map<String, Object> expectedArgs) {
        // Mock the tool service to return our data lake tools
        when(mcpToolService.getTools(dataLakeServer)).thenReturn(dataLakeTools);
        
        // Mock the intelligent selector to return the expected tool and arguments
        when(intelligentToolSelector.selectBestTool(anyString(), eq(dataLakeTools), eq(dataLakeServer)))
            .thenReturn(expectedTool);
        when(intelligentToolSelector.extractToolArguments(anyString(), eq(expectedTool), any(), eq(dataLakeServer)))
            .thenReturn(expectedArgs);
        
        // Mock the tool execution to return a recognizable result
        try {
            when(mcpToolService.callToolViaHttp(eq(dataLakeServer), eq(expectedTool), eq(expectedArgs)))
                .thenReturn("Mock tool execution: " + expectedTool + " with " + expectedArgs);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}