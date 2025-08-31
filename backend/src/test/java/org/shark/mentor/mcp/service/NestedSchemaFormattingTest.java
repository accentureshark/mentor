package org.shark.mentor.mcp.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.shark.mentor.mcp.application.service.formatting.ResponseFormatterService;
import org.shark.mentor.mcp.application.service.i18n.I18nService;
import org.shark.mentor.mcp.domain.model.McpServer;
import org.shark.mentor.mcp.infraestructure.config.UiProperties;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test to ensure schema formatting works with nested JSON structures like result.schemas
 */
class NestedSchemaFormattingTest {

    private ResponseFormatterService responseFormatterService;
    private I18nService i18nService;
    private ObjectMapper objectMapper;
    private McpServer testServer;

    @BeforeEach
    void setUp() {
        UiProperties uiProperties = new UiProperties();
        uiProperties.setLocale("en");
        i18nService = new I18nService(uiProperties);
        responseFormatterService = new ResponseFormatterService(i18nService);
        objectMapper = new ObjectMapper();
        
        testServer = new McpServer();
        testServer.setName("Polenta MCP Server (Local)");
    }

    @Test
    void testNestedSchemaJsonFormatting() throws Exception {
        // Given - JSON response with schemas nested under result.schemas (the exact structure from problem statement)
        String jsonResponse = """
        {
          "result" : {
            "schemas" : [ "information_schema", "sf1", "sf100", "sf1000", "sf10000", "sf100000", "sf300", "sf3000", "sf30000", "tiny" ],
            "status" : "success"
          },
          "trace_id" : "e6a7ccb6-f62d-44fc-b2c1-b64b953c1610",
          "id" : "5421a143-1dd1-4505-9d88-11971fab67d0",
          "jsonrpc" : "2.0"
        }
        """;

        // When - Try to format without LLM
        String formatted = responseFormatterService.tryFormatWithoutLlm(jsonResponse, "dame todos los esquemas", testServer);

        // Then - Should format schemas properly, not fall back to generic JSON
        assertNotNull(formatted, "Should format without LLM");
        assertTrue(formatted.contains("🏗️ Schemas Disponibles"), "Should contain schema header");
        assertTrue(formatted.contains("📁 **information_schema**"), "Should format information_schema");
        assertTrue(formatted.contains("📁 **sf1**"), "Should format sf1");
        assertTrue(formatted.contains("📁 **tiny**"), "Should format tiny");
        assertFalse(formatted.contains("```json"), "Should not display raw JSON");
    }

    @Test
    void testDirectSchemaArrayStillWorksAfterFix() throws Exception {
        // Given - Direct schema array (existing format that should keep working)
        String jsonResponse = """
        [ "information_schema", "sf1", "sf100", "tiny" ]
        """;

        // When - Try to format without LLM
        String formatted = responseFormatterService.tryFormatWithoutLlm(jsonResponse, "list schemas", testServer);

        // Then - Should still format schemas properly
        assertNotNull(formatted, "Should format without LLM");
        assertTrue(formatted.contains("🏗️ Schemas Disponibles"), "Should contain schema header");
        assertTrue(formatted.contains("📁 **information_schema**"), "Should format information_schema");
        assertTrue(formatted.contains("📁 **sf1**"), "Should format sf1");
        assertFalse(formatted.contains("```json"), "Should not display raw JSON");
    }

    @Test
    void testObjectWithSchemasPropertyStillWorksAfterFix() throws Exception {
        // Given - Object with direct schemas property (another existing format)
        String jsonResponse = """
        {
          "schemas": [ "information_schema", "sf1", "sf100", "tiny" ]
        }
        """;

        // When - Try to format without LLM
        String formatted = responseFormatterService.tryFormatWithoutLlm(jsonResponse, "show schemas", testServer);

        // Then - Should still format schemas properly
        assertNotNull(formatted, "Should format without LLM");
        assertTrue(formatted.contains("🏗️ Schemas Disponibles"), "Should contain schema header");
        assertTrue(formatted.contains("📁 **information_schema**"), "Should format information_schema");
        assertFalse(formatted.contains("```json"), "Should not display raw JSON");
    }
}