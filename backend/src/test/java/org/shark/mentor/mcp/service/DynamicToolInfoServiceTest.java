package org.shark.mentor.mcp.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.shark.mentor.mcp.application.service.notification.DynamicToolInfoService;
import org.shark.mentor.mcp.application.service.tool.McpToolService;
import org.shark.mentor.mcp.domain.model.McpServer;



import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Test for dynamic tool information service that replaces hardcoded type checks
 */
@ExtendWith(MockitoExtension.class)
class DynamicToolInfoServiceTest {

    @Mock
    private McpToolService mcpToolService;

    private DynamicToolInfoService dynamicToolInfoService;
    private    McpServer testServer;

    @BeforeEach
    void setUp() {
        dynamicToolInfoService = new DynamicToolInfoService(mcpToolService);
        testServer = new McpServer("test-server", "Test Server", "Test MCP Server", "http://localhost:8080", "active");
    }

    @Test
    void testAnalyzeToolContext_DatabaseTools() {
        // Setup database-related tools
        List<Map<String, Object>> databaseTools = List.of(
            Map.of("name", "list_tables", "description", "Lista todas las tablas disponibles en el data lake"),
            Map.of("name", "describe_table", "description", "Devuelve la estructura de una tabla específica"),
            Map.of("name", "query_data", "description", "Ejecuta una consulta en lenguaje natural o SQL"),
            Map.of("name", "list_schemas", "description", "Lista todos los esquemas disponibles")
        );

        when(mcpToolService.getTools(any(McpServer.class))).thenReturn(databaseTools);

        DynamicToolInfoService.ToolContext context = dynamicToolInfoService.analyzeToolContext(testServer, "table schema information");

        // Verify domain detection
        assertTrue(context.getDomains().contains("database"));
        
        // Verify data type detection
        assertTrue(context.getDataTypes().contains("table"));
        assertTrue(context.getDataTypes().contains("schema"));
        
        // Verify operation detection
        assertTrue(context.getOperations().contains("list"));
        assertTrue(context.getOperations().contains("describe"));
        assertTrue(context.getOperations().contains("query"));

        verify(mcpToolService, times(1)).getTools(testServer);
    }

    @Test
    void testAnalyzeToolContext_GitHubTools() {
        // Setup GitHub-related tools
        List<Map<String, Object>> githubTools = List.of(
            Map.of("name", "list_repositories", "description", "Lista repositorios en GitHub"),
            Map.of("name", "search_repositories", "description", "Busca repositorios por nombre"),
            Map.of("name", "get_file_contents", "description", "Obtiene el contenido de un archivo del repositorio")
        );

        when(mcpToolService.getTools(any(McpServer.class))).thenReturn(githubTools);

        DynamicToolInfoService.ToolContext context = dynamicToolInfoService.analyzeToolContext(testServer, "repository code information");

        // Verify domain detection
        assertTrue(context.getDomains().contains("repository"));
        assertTrue(context.getDomains().contains("github"));
        
        // Verify operations
        assertTrue(context.getOperations().contains("list"));
        assertTrue(context.getOperations().contains("search"));

        verify(mcpToolService, times(1)).getTools(testServer);
    }

    @Test
    void testBuildDynamicFormatInstructions_Database() {
        DynamicToolInfoService.ToolContext toolContext = new DynamicToolInfoService.ToolContext();
        toolContext.getDomains().add("database");
        toolContext.getDataTypes().add("table");
        toolContext.getDataTypes().add("schema");
        toolContext.getOperations().add("query");

        String instructions = dynamicToolInfoService.buildDynamicFormatInstructions(toolContext, "Test Server");

        assertNotNull(instructions);
        assertTrue(instructions.contains("database/table information"));
        assertTrue(instructions.contains("📁"));
        assertTrue(instructions.contains("🏗️"));
        assertTrue(instructions.contains("query results"));
        assertTrue(instructions.contains("📊"));
    }

    @Test
    void testGetContextEmoji_ByDataType() {
        DynamicToolInfoService.ToolContext toolContext = new DynamicToolInfoService.ToolContext();
        
        // Test table/schema context
        toolContext.getDataTypes().add("table");
        assertEquals("📁", dynamicToolInfoService.getContextEmoji(toolContext));
        
        // Test query context
        toolContext.getDataTypes().clear();
        toolContext.getOperations().add("query");
        assertEquals("📊", dynamicToolInfoService.getContextEmoji(toolContext));
        
        // Test repository context
        toolContext.getOperations().clear();
        toolContext.getDomains().add("repository");
        assertEquals("💻", dynamicToolInfoService.getContextEmoji(toolContext));
        
        // Test default
        toolContext.getDomains().clear();
        assertEquals("💡", dynamicToolInfoService.getContextEmoji(toolContext));
    }

    @Test
    void testNoDuplicateAnalysis() {
        // Test that the service doesn't add duplicates when analyzing
        List<Map<String, Object>> toolsWithDuplicateKeywords = List.of(
            Map.of("name", "list_tables_and_schemas", "description", "Lista tablas y esquemas con información de tabla")
        );

        when(mcpToolService.getTools(any(McpServer.class))).thenReturn(toolsWithDuplicateKeywords);

        DynamicToolInfoService.ToolContext context = dynamicToolInfoService.analyzeToolContext(testServer, "table information");

        // Should not have duplicates
        assertEquals(1, context.getDataTypes().stream().filter(type -> type.equals("table")).count());
    }
}