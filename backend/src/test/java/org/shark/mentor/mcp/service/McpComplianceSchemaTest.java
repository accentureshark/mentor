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
 * Test to ensure MCP compliance by verifying that the exact JSON structure 
 * from the problem statement is handled correctly without hardcoded assumptions.
 */
class McpComplianceSchemaTest {

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
    void testExactJsonFromProblemStatement() throws Exception {
        // Given - The exact JSON response from the problem statement
        String jsonResponse = """
        {
          "result" : {
            "schemas" : [ "information_schema", "sf1", "sf100", "sf1000", "sf10000", "sf100000", "sf300", "sf3000", "sf30000", "tiny" ],
            "status" : "success"
          },
          "trace_id" : "67c6df71-d16b-4a31-946b-f67f6465fb2a",
          "id" : "b1ac50c9-05cf-4a5c-b163-9c5c60fac470",
          "jsonrpc" : "2.0"
        }
        """;

        // When - Try to format without LLM (should be detected as schema response)
        String formatted = responseFormatterService.tryFormatWithoutLlm(jsonResponse, "dame todos los esquemas", testServer);

        // Then - Should format properly as a schema list, not generic JSON
        assertNotNull(formatted, "Should format without LLM for schema responses");
        assertTrue(formatted.contains("🏗️ Schemas Disponibles"), "Should contain schema header");
        assertFalse(formatted.contains("```json"), "Should not display raw JSON when schemas are detected");
        
        // Should contain all schemas from the response
        assertTrue(formatted.contains("📁 **information_schema**"), "Should format information_schema");
        assertTrue(formatted.contains("📁 **sf1**"), "Should format sf1");
        assertTrue(formatted.contains("📁 **sf100**"), "Should format sf100");
        assertTrue(formatted.contains("📁 **tiny**"), "Should format tiny");
        
        // Should show proper server attribution
        assertTrue(formatted.contains("Polenta MCP Server (Local)"), "Should show correct server name");
    }

    @Test
    void testGenericSchemaListWithDifferentNames() throws Exception {
        // Given - A schema response with completely different schema names (test MCP compliance)
        String jsonResponse = """
        {
          "result" : {
            "schemas" : [ "information_schema", "sales_db", "inventory", "users", "analytics", "logs" ],
            "status" : "success"
          },
          "trace_id" : "test-trace-id",
          "id" : "test-id",
          "jsonrpc" : "2.0"
        }
        """;

        // When - Try to format without LLM
        String formatted = responseFormatterService.tryFormatWithoutLlm(jsonResponse, "list all schemas", testServer);

        // Then - Should still format properly (proving it's not hardcoded to specific names)
        assertNotNull(formatted, "Should format without LLM for any schema response");
        assertTrue(formatted.contains("🏗️ Schemas Disponibles"), "Should contain schema header");
        assertFalse(formatted.contains("```json"), "Should not display raw JSON");
        
        // Should contain all schemas regardless of their names
        assertTrue(formatted.contains("📁 **information_schema**"), "Should format information_schema");
        assertTrue(formatted.contains("📁 **sales_db**"), "Should format sales_db");
        assertTrue(formatted.contains("📁 **inventory**"), "Should format inventory");
        assertTrue(formatted.contains("📁 **users**"), "Should format users");
        assertTrue(formatted.contains("📁 **analytics**"), "Should format analytics");
        assertTrue(formatted.contains("📁 **logs**"), "Should format logs");
    }

    @Test
    void testDifferentServerAttributionIsGeneric() throws Exception {
        // Given - Same JSON but different server name
        McpServer differentServer = new McpServer();
        differentServer.setName("Custom Database Server");
        
        String jsonResponse = """
        {
          "result" : {
            "schemas" : [ "information_schema", "app_data", "user_profiles" ],
            "status" : "success"
          }
        }
        """;

        // When - Format with different server
        String formatted = responseFormatterService.tryFormatWithoutLlm(jsonResponse, "show schemas", differentServer);

        // Then - Should use the actual server name, not hardcoded one
        assertNotNull(formatted, "Should format without LLM");
        assertTrue(formatted.contains("Custom Database Server"), "Should use actual server name");
        assertFalse(formatted.contains("Polenta"), "Should not contain hardcoded server names");
        assertTrue(formatted.contains("📁 **app_data**"), "Should format schemas");
    }

    @Test 
    void testEmptySchemaArrayIsHandledGracefully() throws Exception {
        // Given - Valid JSON structure but no schemas
        String jsonResponse = """
        {
          "result" : {
            "schemas" : [],
            "status" : "success"
          }
        }
        """;

        // When - Try to format
        String formatted = responseFormatterService.tryFormatWithoutLlm(jsonResponse, "list schemas", testServer);

        // Then - Should still handle gracefully
        assertNotNull(formatted, "Should handle empty schema list");
        assertTrue(formatted.contains("🏗️ Schemas Disponibles"), "Should contain schema header");
    }
}