package org.shark.mentor.mcp.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.shark.mentor.mcp.application.service.chat.ChatService;
import org.shark.mentor.mcp.application.service.formatting.ResponseFormatterService;
import org.shark.mentor.mcp.application.service.i18n.I18nService;
import org.shark.mentor.mcp.domain.model.McpServer;
import org.shark.mentor.mcp.infraestructure.config.UiProperties;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test to verify that the enhanced schema formatting works end-to-end
 */
class EnhancedSchemaFormattingIntegrationTest {

    private I18nService i18nService;
    private ResponseFormatterService responseFormatterService;
    private ObjectMapper objectMapper;
    private McpServer polentaServer;

    @BeforeEach
    void setUp() {
        UiProperties uiProperties = new UiProperties();
        uiProperties.setLocale("en");
        i18nService = new I18nService(uiProperties);
        responseFormatterService = new ResponseFormatterService(i18nService);
        objectMapper = new ObjectMapper();
        
        polentaServer = McpServer.builder()
                .id("polenta-local")
                .name("Polenta MCP Server (Local)")
                .url("http://localhost:3000")
                .status("CONNECTED")
                .build();
    }

    @Test
    void testPolentaServerSchemaResponseFormatting() throws Exception {
        // Given - The exact JSON response from Polenta MCP Server
        String polentaResponse = """
        {
          "result" : {
            "schemas" : [ "information_schema", "sf1", "sf100", "sf1000", "sf10000", "sf100000", "sf300", "sf3000", "sf30000", "tiny" ],
            "status" : "success"
          },
          "trace_id" : "ef058002-d944-4f31-aa28-75db894dd11f",
          "id" : "8138be7a-e0aa-483c-968b-d1dcc7f461cd",
          "jsonrpc" : "2.0"
        }
        """;

        String userMessage = "listame todos los esquemas";

        // When - Try to format without LLM using ResponseFormatterService
        String formattedResponse = responseFormatterService.tryFormatWithoutLlm(polentaResponse, userMessage, polentaServer);

        // Then - Should produce user-friendly formatting
        assertNotNull(formattedResponse, "ResponseFormatterService should handle this response");
        
        // Verify user-friendly formatting
        assertFalse(formattedResponse.contains("trace_id"), "Should filter out technical metadata");
        assertFalse(formattedResponse.contains("jsonrpc"), "Should filter out protocol details");
        assertFalse(formattedResponse.contains("\"result\""), "Should not show raw JSON structure");
        
        // Should contain enhanced formatting
        assertTrue(formattedResponse.contains("🗃️"), "Should use enhanced schema icons");
        assertTrue(formattedResponse.contains("information_schema"), "Should contain all schema names");
        assertTrue(formattedResponse.contains("sf1"), "Should contain all schema names");
        assertTrue(formattedResponse.contains("tiny"), "Should contain all schema names");
        assertTrue(formattedResponse.contains("Database Schema") || formattedResponse.contains("Esquema de Base de Datos"), 
                  "Should describe what schemas are");

        System.out.println("=== Enhanced Polenta Schema Response ===");
        System.out.println(formattedResponse);
        System.out.println("======================================");
    }

    @Test
    void testSpanishLocaleSchemaFormatting() throws Exception {
        // Given - Spanish locale configuration
        UiProperties spanishProps = new UiProperties();
        spanishProps.setLocale("es");
        I18nService spanishI18n = new I18nService(spanishProps);
        ResponseFormatterService spanishFormatter = new ResponseFormatterService(spanishI18n);

        String polentaResponse = """
        {
          "result" : {
            "schemas" : [ "information_schema", "sf1", "tiny" ],
            "status" : "success"
          }
        }
        """;

        String userMessage = "listame los esquemas";

        // When - Format with Spanish locale
        String formattedResponse = spanishFormatter.tryFormatWithoutLlm(polentaResponse, userMessage, polentaServer);

        // Then - Should use Spanish text where appropriate
        assertNotNull(formattedResponse);
        assertTrue(formattedResponse.contains("Esquemas") || formattedResponse.contains("Schemas"), 
                  "Should contain schema-related text");

        System.out.println("=== Spanish Locale Schema Response ===");
        System.out.println(formattedResponse);
        System.out.println("====================================");
    }

    @Test
    void testLegacySchemaArrayFormatting() throws Exception {
        // Given - Legacy direct array format
        String legacyResponse = """
        [ "information_schema", "sf1", "sf100", "tiny" ]
        """;

        String userMessage = "show schemas";

        // When - Format legacy response
        String formattedResponse = responseFormatterService.tryFormatWithoutLlm(legacyResponse, userMessage, polentaServer);

        // Then - Should still handle it properly
        assertNotNull(formattedResponse);
        assertTrue(formattedResponse.contains("information_schema"));
        assertTrue(formattedResponse.contains("🗃️"), "Should use enhanced formatting even for legacy responses");

        System.out.println("=== Legacy Schema Array Response ===");
        System.out.println(formattedResponse);
        System.out.println("==================================");
    }

    @Test
    void testComplexSchemaResponseWithStatus() throws Exception {
        // Given - More complex response with additional metadata
        String complexResponse = """
        {
          "result": {
            "schemas": [
              {
                "name": "information_schema",
                "type": "system"
              },
              {
                "name": "user_data",
                "type": "user"
              }
            ],
            "count": 2,
            "status": "success"
          },
          "timestamp": "2024-01-01T00:00:00Z"
        }
        """;

        String userMessage = "get all schemas";

        // When - Format complex response
        String formattedResponse = responseFormatterService.tryFormatWithoutLlm(complexResponse, userMessage, polentaServer);

        // Then - Should extract schema names properly
        assertNotNull(formattedResponse);
        assertTrue(formattedResponse.contains("information_schema"));
        assertTrue(formattedResponse.contains("user_data"));
        assertFalse(formattedResponse.contains("timestamp"), "Should filter out metadata");

        System.out.println("=== Complex Schema Response ===");
        System.out.println(formattedResponse);
        System.out.println("=============================");
    }

    @Test
    void testNonSchemaResponseFormatsAsGeneric() throws Exception {
        // Given - Non-schema response
        String nonSchemaResponse = """
        {
          "result": {
            "tables": [ "users", "orders", "products" ],
            "status": "success"
          }
        }
        """;

        String userMessage = "list tables";

        // When - Try to format (will format as generic JSON, not as schema)
        String formattedResponse = responseFormatterService.tryFormatWithoutLlm(nonSchemaResponse, userMessage, polentaServer);

        // Then - Should format as generic JSON (not as schema)
        assertNotNull(formattedResponse, "Should format as generic JSON response");
        assertFalse(formattedResponse.contains("🗃️"), "Should not use schema-specific formatting");
        assertTrue(formattedResponse.contains("📊 Información Estructurada") || formattedResponse.contains("Structured information"), 
                  "Should use generic structured formatting");
        assertTrue(formattedResponse.contains("tables"), "Should contain the original content");

        System.out.println("=== Non-Schema JSON Response (formatted as generic) ===");
        System.out.println(formattedResponse);
        System.out.println("======================================================");
    }
}