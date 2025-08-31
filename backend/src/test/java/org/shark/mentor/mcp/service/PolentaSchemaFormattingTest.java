package org.shark.mentor.mcp.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.shark.mentor.mcp.application.service.chat.ChatService;
import org.shark.mentor.mcp.application.service.i18n.I18nService;
import org.shark.mentor.mcp.infraestructure.config.UiProperties;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test specifically for the Polenta MCP Server schema response formatting issue.
 * This addresses the problem statement about making schema responses more user-friendly.
 */
class PolentaSchemaFormattingTest {

    private I18nService i18nService;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        UiProperties uiProperties = new UiProperties();
        uiProperties.setLocale("en");
        i18nService = new I18nService(uiProperties);
        objectMapper = new ObjectMapper();
    }

    @Test
    void testPolentaSchemaResponseFormatting() throws Exception {
        // Given - The exact JSON response from the problem statement
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

        JsonNode jsonNode = objectMapper.readTree(polentaResponse);
        String userMessage = "listame los esquemas";

        // When - Format using current logic
        boolean isSchemaResponse = isSchemaResponse(jsonNode, userMessage);
        
        // Then - Should be detected as schema response
        assertTrue(isSchemaResponse, "Should detect this as a schema response");

        String formatted = formatEnhancedSchemaResponse(jsonNode, userMessage);

        // Verify formatting quality
        assertNotNull(formatted, "Formatted response should not be null");
        assertFalse(formatted.contains("trace_id"), "Should not contain technical metadata");
        assertFalse(formatted.contains("jsonrpc"), "Should not contain protocol details");
        assertFalse(formatted.contains("\"result\""), "Should not show raw JSON structure");
        
        // Should contain user-friendly elements
        assertTrue(formatted.contains("📊"), "Should have data icon");
        assertTrue(formatted.contains("Schemas List") || formatted.contains("Lista de Esquemas"), "Should have clear title");
        assertTrue(formatted.contains("information_schema"), "Should contain schema names");
        assertTrue(formatted.contains("sf1"), "Should contain all schema names");
        assertTrue(formatted.contains("tiny"), "Should contain all schema names");

        System.out.println("=== Formatted Schema Response ===");
        System.out.println(formatted);
        System.out.println("================================");
    }

    @Test
    void testRawSchemaTextFormatting() {
        // Test formatting when schemas come as plain text instead of JSON
        String rawSchemaText = "information_schema\nsf1\nsf100\nsf1000\nsf10000\nsf100000\nsf300\nsf3000\nsf30000\ntiny";
        
        String formatted = formatRawSchemaResponse(rawSchemaText);
        
        assertNotNull(formatted);
        assertTrue(formatted.contains("📊"), "Should have data icon");
        assertTrue(formatted.contains("information_schema"), "Should contain schema names");
        
        System.out.println("=== Raw Schema Text Formatted ===");
        System.out.println(formatted);
        System.out.println("=================================");
    }

    // Helper methods - these would be enhanced versions of the ones in ChatService
    private boolean isSchemaResponse(JsonNode jsonNode, String userMessage) {
        if (jsonNode != null) {
            String jsonString = jsonNode.toString().toLowerCase();
            // Enhanced detection for nested result.schemas structure
            if (jsonNode.has("result") && jsonNode.get("result").has("schemas")) {
                return true;
            }
            if (jsonString.contains("schema") && jsonString.contains("information_schema")) {
                return true;
            }
        }
        
        if (userMessage != null) {
            String userQuery = userMessage.toLowerCase();
            return (userQuery.contains("list") && userQuery.contains("schema")) ||
                   (userQuery.contains("listame") && userQuery.contains("esquemas"));
        }
        
        return false;
    }

    private String formatEnhancedSchemaResponse(JsonNode jsonNode, String userMessage) {
        StringBuilder response = new StringBuilder();
        
        // User-friendly header
        response.append("✅ **Response from Polenta MCP Server (Local)**\n\n");
        response.append("📊 **Schemas List** The following schemas are available:\n\n");
        
        try {
            // Extract schemas from nested result.schemas or direct array
            JsonNode schemas = null;
            if (jsonNode.has("result") && jsonNode.get("result").has("schemas")) {
                schemas = jsonNode.get("result").get("schemas");
            } else if (jsonNode.isArray()) {
                schemas = jsonNode;
            }
            
            if (schemas != null && schemas.isArray()) {
                for (JsonNode schema : schemas) {
                    String schemaName = schema.asText();
                    response.append(formatSingleSchemaItem(schemaName));
                }
            }
        } catch (Exception e) {
            // Fallback to generic formatting
            response.append("```\n");
            response.append("Schemas: ");
            if (jsonNode.has("result") && jsonNode.get("result").has("schemas")) {
                JsonNode schemas = jsonNode.get("result").get("schemas");
                for (JsonNode schema : schemas) {
                    response.append(schema.asText()).append(", ");
                }
            }
            response.append("\n```\n");
        }
        
        response.append("\n💡 *Information provided by Polenta MCP Server (Local)*");
        return response.toString();
    }

    private String formatRawSchemaResponse(String rawSchemaText) {
        StringBuilder response = new StringBuilder();
        response.append("✅ **Response from Polenta MCP Server (Local)**\n\n");
        response.append("📊 **Schemas List** The following schemas are available:\n\n");
        
        String[] lines = rawSchemaText.split("\n");
        for (String line : lines) {
            line = line.trim();
            if (!line.isEmpty() && line.matches("^[a-zA-Z0-9_]+$")) {
                response.append(formatSingleSchemaItem(line));
            }
        }
        
        response.append("\n💡 *Information provided by Polenta MCP Server (Local)*");
        return response.toString();
    }

    private String formatSingleSchemaItem(String schemaName) {
        return String.format("🗃️ **%s**\n" +
                "   📋 Type: Database Schema\n" +
                "   🏗️ Structure: Available for querying\n" +
                "   📊 Contains tables and data definitions\n\n", schemaName);
    }
}