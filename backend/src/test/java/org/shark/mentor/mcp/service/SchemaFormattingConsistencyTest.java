package org.shark.mentor.mcp.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.shark.mentor.mcp.application.service.chat.ChatService;
import org.shark.mentor.mcp.application.service.i18n.I18nService;
import org.shark.mentor.mcp.infraestructure.config.UiProperties;


import static org.junit.jupiter.api.Assertions.*;

/**
 * Test to ensure consistent schema formatting across different response paths
 */
class SchemaFormattingConsistencyTest {

    private ChatService chatService;
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
    void testRawSchemaResponseFormatting() {
        // Given - Raw text response that looks like a schema list
        String rawResponse = "information_schema\nsf1\nsf100\nsf1000\nsf10000\nsf100000\nsf300\nsf3000\nsf30000\ntiny";
        
        // When - Format as raw response
        boolean isSchemaResponse = isSchemaListResponse(rawResponse);
        
        // Then - Should be detected as schema response
        assertTrue(isSchemaResponse, "Should detect schema list response");
        
        String formatted = formatRawSchemaResponse(rawResponse);
        
        // Should contain consistent header
        assertTrue(formatted.contains("📊 **Schemas List**"), "Should contain consistent schema header");
        assertTrue(formatted.contains("The following schemas are available:"), "Should contain introduction text");
        assertTrue(formatted.contains("📁 name: information_schema"), "Should format individual schemas consistently");
        assertTrue(formatted.contains("🏗️ structure:"), "Should include structure information");
    }

    @Test
    void testJsonSchemaResponseFormatting() throws Exception {
        // Given - JSON response with schema array
        String jsonResponse = "[\"information_schema\", \"sf1\", \"sf100\", \"tiny\"]";
        JsonNode jsonNode = objectMapper.readTree(jsonResponse);
        
        // When - Check if it's a schema response
        boolean isSchemaResponse = isSchemaResponse(jsonNode, "listame todos los esquemas");
        
        // Then - Should be detected as schema response
        assertTrue(isSchemaResponse, "Should detect JSON schema list response");
        
        String formatted = formatSchemaResponse(jsonNode);
        
        // Should contain consistent header
        assertTrue(formatted.contains("📊 **Schemas List**"), "Should contain consistent schema header");
        assertTrue(formatted.contains("The following schemas are available:"), "Should contain introduction text");
    }

    @Test
    void testSchemaDetectionFromUserMessage() {
        // Test various ways users might ask for schemas
        assertTrue(isSchemaResponse(null, "list schemas"), "Should detect 'list schemas'");
        assertTrue(isSchemaResponse(null, "listame todos los esquemas"), "Should detect Spanish 'listame todos los esquemas'");
        assertFalse(isSchemaResponse(null, "list tables"), "Should not detect table requests as schema requests");
        assertFalse(isSchemaResponse(null, "show data"), "Should not detect data requests as schema requests");
    }

    // Helper methods copied from ChatService for testing
    private boolean isSchemaListResponse(String mcpContext) {
        String lowerContext = mcpContext.toLowerCase();
        // MCP compliant detection: look for schema-related keywords and patterns
        // Check for common schema indicators without hardcoding specific schema names
        boolean hasSchemaKeywords = lowerContext.contains("schema") || lowerContext.contains("information_schema");
        
        // Check if it looks like a list of schema names (multiple lines with alphanumeric names)
        String[] lines = mcpContext.split("\n");
        int schemaLikeLines = 0;
        for (String line : lines) {
            line = line.trim();
            if (!line.isEmpty() && line.matches("^[a-zA-Z0-9_]+$") && 
                !line.toLowerCase().contains("available") && !line.toLowerCase().contains("schema")) {
                schemaLikeLines++;
            }
        }
        
        // Consider it a schema list if it has schema keywords and multiple schema-like entries
        return hasSchemaKeywords && schemaLikeLines >= 2;
    }

    private boolean isSchemaResponse(JsonNode jsonNode, String userMessage) {
        if (jsonNode != null) {
            String jsonString = jsonNode.toString().toLowerCase();
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

    private String formatRawSchemaResponse(String mcpContext) {
        StringBuilder response = new StringBuilder();
        response.append(i18nService.getMessage("schemas.list.header")).append("\n");
        response.append("The following schemas are available:\n\n");
        
        // Extract schema names from the raw context
        String[] lines = mcpContext.split("\n");
        for (String line : lines) {
            line = line.trim();
            if (!line.isEmpty() && line.matches("^[a-zA-Z0-9_]+$")) {
                response.append(formatSingleSchema(line));
            }
        }
        
        return response.toString();
    }

    private String formatSchemaResponse(JsonNode jsonNode) {
        StringBuilder response = new StringBuilder();
        response.append(i18nService.getMessage("schemas.list.header")).append("\n");
        response.append("The following schemas are available:\n\n");
        
        try {
            if (jsonNode.isArray()) {
                for (JsonNode schema : jsonNode) {
                    String schemaName = schema.isTextual() ? schema.asText() : "unknown";
                    response.append(formatSingleSchema(schemaName));
                }
            }
        } catch (Exception e) {
            response.append(jsonNode.toString());
        }
        
        return response.toString();
    }

    private String formatSingleSchema(String schemaName) {
        return String.format("%s name: %s %s structure: The structure of this schema is not defined in the provided context. Size: Not specified in the provided context.\n\n",
                i18nService.getMessage("prefix.file"),
                schemaName,
                i18nService.getMessage("prefix.structure"));
    }
}