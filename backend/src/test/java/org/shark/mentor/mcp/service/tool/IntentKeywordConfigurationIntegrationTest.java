package org.shark.mentor.mcp.service.tool;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.shark.mentor.mcp.application.service.tool.IntentKeywordService;
import org.shark.mentor.mcp.infraestructure.config.IntentKeywordProperties;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test to verify that the YAML configuration loads correctly from application.yml
 */
@SpringBootTest
class IntentKeywordConfigurationIntegrationTest {

    @Autowired
    private IntentKeywordProperties intentKeywordProperties;

    @Autowired
    private IntentKeywordService intentKeywordService;

    @Test
    void shouldLoadIntentKeywordsFromApplicationYml() {
        // Verify that configuration is loaded
        assertNotNull(intentKeywordProperties);
        assertNotNull(intentKeywordProperties.getIntents());
        
        // Verify specific intent configurations are loaded
        IntentKeywordProperties.IntentDefinition listSchemas = intentKeywordProperties.getIntents().getLIST_SCHEMAS();
        assertNotNull(listSchemas);
        assertNotNull(listSchemas.getKeywords());
        
        // Check Spanish keywords
        List<String> spanishKeywords = listSchemas.getKeywords().getSpanish();
        assertNotNull(spanishKeywords);
        assertTrue(spanishKeywords.contains("esquemas"));
        assertTrue(spanishKeywords.contains("cuales son los esquemas"));
        
        // Check English keywords
        List<String> englishKeywords = listSchemas.getKeywords().getEnglish();
        assertNotNull(englishKeywords);
        assertTrue(englishKeywords.contains("schemas"));
        assertTrue(englishKeywords.contains("list schemas"));
        
        // Check tool names
        List<String> toolNames = listSchemas.getToolNames();
        assertNotNull(toolNames);
        assertTrue(toolNames.contains("list_schemas"));
    }

    @Test
    void shouldDetectIntentsUsingLoadedConfiguration() {
        List<Map<String, Object>> availableTools = List.of(
            Map.of("name", "list_schemas", "description", "Lista esquemas"),
            Map.of("name", "list_tables", "description", "Lista tablas"),
            Map.of("name", "describe_table", "description", "Describe tabla"),
            Map.of("name", "query_data", "description", "Query datos")
        );

        // Test Spanish intent detection
        String spanishSchemaQuery = "cuales son los esquemas";
        String result = intentKeywordService.detectIntentAndGetTool(spanishSchemaQuery, availableTools);
        assertEquals("list_schemas", result);

        // Test English intent detection
        String englishTableQuery = "list tables";
        result = intentKeywordService.detectIntentAndGetTool(englishTableQuery, availableTools);
        assertEquals("list_tables", result);

        // Test describe intent
        String describeQuery = "describe table structure";
        result = intentKeywordService.detectIntentAndGetTool(describeQuery, availableTools);
        assertEquals("describe_table", result);
    }

    @Test
    void shouldHavePatternConfigurationLoaded() {
        // Verify patterns are loaded
        assertNotNull(intentKeywordProperties.getPatterns());
        assertNotNull(intentKeywordProperties.getPatterns().getSchema_response());
        
        IntentKeywordProperties.PatternDefinition schemaPattern = intentKeywordProperties.getPatterns().getSchema_response();
        assertNotNull(schemaPattern.getKeywords());
        assertNotNull(schemaPattern.getCombined_checks());
        assertTrue(schemaPattern.getCombined_checks().contains("information_schema"));
    }

    @Test
    void shouldHaveFallbackConfigurationLoaded() {
        // Verify fallback configuration
        assertNotNull(intentKeywordProperties.getFallback());
        assertNotNull(intentKeywordProperties.getFallback().getDefault_tool_selection_order());
        
        List<String> defaultOrder = intentKeywordProperties.getFallback().getDefault_tool_selection_order();
        assertTrue(defaultOrder.contains("query_data"));
        assertTrue(defaultOrder.contains("list_tables"));
        assertTrue(defaultOrder.contains("describe_table"));
    }

    @Test
    void shouldWorkWithSchemaResponseDetection() {
        // Test schema response detection
        String jsonResponse = "{\"schemas\": [\"information_schema\", \"public\"]}";
        String userMessage = "listame los esquemas";
        
        boolean isSchemaResponse = intentKeywordService.isSchemaResponse(jsonResponse, userMessage);
        assertTrue(isSchemaResponse);
    }
}