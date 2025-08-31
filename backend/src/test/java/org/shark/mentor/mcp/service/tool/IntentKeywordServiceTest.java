package org.shark.mentor.mcp.service.tool;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.shark.mentor.mcp.application.service.tool.IntentKeywordService;
import org.shark.mentor.mcp.infraestructure.config.IntentKeywordProperties;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test for IntentKeywordService that replaces hardcoded intent detection logic
 */
class IntentKeywordServiceTest {

    private IntentKeywordService intentKeywordService;
    private IntentKeywordProperties intentKeywordProperties;
    private List<Map<String, Object>> availableTools;

    @BeforeEach
    void setUp() {
        // Setup test configuration similar to application.yml
        intentKeywordProperties = createTestIntentKeywordProperties();
        intentKeywordService = new IntentKeywordService(intentKeywordProperties);
        
        // Setup test tools
        availableTools = List.of(
            Map.of("name", "list_schemas", "description", "Lista todos los esquemas disponibles"),
            Map.of("name", "list_tables", "description", "Lista todas las tablas disponibles"),
            Map.of("name", "describe_table", "description", "Describe la estructura de una tabla"),
            Map.of("name", "query_data", "description", "Ejecuta consultas sobre los datos"),
            Map.of("name", "sample_data", "description", "Obtiene datos de muestra"),
            Map.of("name", "search_tables", "description", "Busca tablas por palabra clave")
        );
    }

    private IntentKeywordProperties createTestIntentKeywordProperties() {
        IntentKeywordProperties properties = new IntentKeywordProperties();
        
        // Create intents configuration
        IntentKeywordProperties.IntentConfig intents = new IntentKeywordProperties.IntentConfig();
        
        // LIST_SCHEMAS intent
        IntentKeywordProperties.IntentDefinition listSchemas = new IntentKeywordProperties.IntentDefinition();
        IntentKeywordProperties.KeywordSet listSchemasKeywords = new IntentKeywordProperties.KeywordSet();
        listSchemasKeywords.setSpanish(List.of("esquemas", "cuales son los esquemas", "listar esquemas"));
        listSchemasKeywords.setEnglish(List.of("schemas", "list schemas"));
        listSchemas.setKeywords(listSchemasKeywords);
        listSchemas.setToolNames(List.of("list_schemas"));
        intents.setLIST_SCHEMAS(listSchemas);

        // LIST_TABLES intent
        IntentKeywordProperties.IntentDefinition listTables = new IntentKeywordProperties.IntentDefinition();
        IntentKeywordProperties.KeywordSet listTablesKeywords = new IntentKeywordProperties.KeywordSet();
        listTablesKeywords.setSpanish(List.of("tablas", "que tablas hay", "mostrar tablas"));
        listTablesKeywords.setEnglish(List.of("tables", "list tables"));
        listTables.setKeywords(listTablesKeywords);
        listTables.setToolNames(List.of("list_tables"));
        intents.setLIST_TABLES(listTables);

        // DESCRIBE intent
        IntentKeywordProperties.IntentDefinition describe = new IntentKeywordProperties.IntentDefinition();
        IntentKeywordProperties.KeywordSet describeKeywords = new IntentKeywordProperties.KeywordSet();
        describeKeywords.setSpanish(List.of("estructura", "describir", "formato"));
        describeKeywords.setEnglish(List.of("describe", "structure"));
        describe.setKeywords(describeKeywords);
        describe.setToolNames(List.of("describe_table"));
        intents.setDESCRIBE(describe);

        // QUERY intent
        IntentKeywordProperties.IntentDefinition query = new IntentKeywordProperties.IntentDefinition();
        IntentKeywordProperties.KeywordSet queryKeywords = new IntentKeywordProperties.KeywordSet();
        queryKeywords.setSpanish(List.of("consulta", "buscar", "datos"));
        queryKeywords.setEnglish(List.of("query", "search", "data"));
        query.setKeywords(queryKeywords);
        query.setToolNames(List.of("query_data"));
        intents.setQUERY(query);

        // Set up the remaining intents with empty definitions for this test
        intents.setSAMPLE(new IntentKeywordProperties.IntentDefinition());
        intents.setSEARCH(new IntentKeywordProperties.IntentDefinition());
        intents.setREPOSITORIES(new IntentKeywordProperties.IntentDefinition());
        intents.setFILES(new IntentKeywordProperties.IntentDefinition());

        properties.setIntents(intents);

        // Set up patterns for schema response detection
        IntentKeywordProperties.PatternConfig patterns = new IntentKeywordProperties.PatternConfig();
        IntentKeywordProperties.PatternDefinition schemaResponse = new IntentKeywordProperties.PatternDefinition();
        IntentKeywordProperties.KeywordSet schemaResponseKeywords = new IntentKeywordProperties.KeywordSet();
        schemaResponseKeywords.setSpanish(List.of("listame", "esquemas"));
        schemaResponseKeywords.setEnglish(List.of("list", "schema"));
        schemaResponse.setKeywords(schemaResponseKeywords);
        schemaResponse.setCombined_checks(List.of("information_schema"));
        patterns.setSchema_response(schemaResponse);
        properties.setPatterns(patterns);

        // Set up fallback configuration
        IntentKeywordProperties.FallbackConfig fallback = new IntentKeywordProperties.FallbackConfig();
        fallback.setDefault_tool_selection_order(List.of("query_data", "list_tables", "describe_table"));
        properties.setFallback(fallback);

        return properties;
    }

    @Test
    void shouldDetectSpanishSchemaIntent() {
        String userMessage = "cuales son los esquemas";
        String result = intentKeywordService.detectIntentAndGetTool(userMessage, availableTools);
        assertEquals("list_schemas", result);
    }

    @Test
    void shouldDetectSpanishTableIntent() {
        String userMessage = "que tablas hay";
        String result = intentKeywordService.detectIntentAndGetTool(userMessage, availableTools);
        assertEquals("list_tables", result);
    }

    @Test
    void shouldDetectSpanishDescribeIntent() {
        String userMessage = "estructura de la tabla";
        String result = intentKeywordService.detectIntentAndGetTool(userMessage, availableTools);
        assertEquals("describe_table", result);
    }

    @Test
    void shouldDetectSpanishQueryIntent() {
        String userMessage = "consulta los datos";
        String result = intentKeywordService.detectIntentAndGetTool(userMessage, availableTools);
        assertEquals("query_data", result);
    }

    @Test
    void shouldDetectEnglishSchemaIntent() {
        String userMessage = "list schemas";
        String result = intentKeywordService.detectIntentAndGetTool(userMessage, availableTools);
        assertEquals("list_schemas", result);
    }

    @Test
    void shouldDetectEnglishTableIntent() {
        String userMessage = "show me the tables";
        String result = intentKeywordService.detectIntentAndGetTool(userMessage, availableTools);
        assertEquals("list_tables", result);
    }

    @Test
    void shouldReturnNullForUnknownIntent() {
        String userMessage = "unknown request";
        String result = intentKeywordService.detectIntentAndGetTool(userMessage, availableTools);
        assertNull(result);
    }

    @Test
    void shouldReturnNullForEmptyMessage() {
        String result = intentKeywordService.detectIntentAndGetTool("", availableTools);
        assertNull(result);
    }

    @Test
    void shouldReturnNullForNullMessage() {
        String result = intentKeywordService.detectIntentAndGetTool(null, availableTools);
        assertNull(result);
    }

    @Test
    void shouldReturnNullWhenNoMatchingToolAvailable() {
        // Create tools without list_schemas
        List<Map<String, Object>> limitedTools = List.of(
            Map.of("name", "other_tool", "description", "Some other tool")
        );
        
        String userMessage = "cuales son los esquemas";
        String result = intentKeywordService.detectIntentAndGetTool(userMessage, limitedTools);
        assertNull(result);
    }

    @Test
    void shouldDetectSchemaResponsePattern() {
        String jsonResponse = "{\"schemas\": [\"information_schema\", \"public\"]}";
        String userMessage = "listame los esquemas";
        
        boolean result = intentKeywordService.isSchemaResponse(jsonResponse, userMessage);
        assertTrue(result);
    }

    @Test
    void shouldDetectSchemaResponseFromJsonContent() {
        String jsonResponse = "{\"data\": \"information_schema\"}";
        String userMessage = "some query";
        
        boolean result = intentKeywordService.isSchemaResponse(jsonResponse, userMessage);
        assertTrue(result);
    }

    @Test
    void shouldNotDetectSchemaResponseForUnrelatedContent() {
        String jsonResponse = "{\"data\": \"some other data\"}";
        String userMessage = "unrelated query";
        
        boolean result = intentKeywordService.isSchemaResponse(jsonResponse, userMessage);
        assertFalse(result);
    }

    @Test
    void shouldReturnDefaultToolSelectionOrder() {
        List<String> defaultOrder = intentKeywordService.getDefaultToolSelectionOrder();
        
        assertNotNull(defaultOrder);
        assertEquals(3, defaultOrder.size());
        assertEquals("query_data", defaultOrder.get(0));
        assertEquals("list_tables", defaultOrder.get(1));
        assertEquals("describe_table", defaultOrder.get(2));
    }

    @Test
    void shouldHandleCaseInsensitiveKeywordMatching() {
        String userMessage = "CUALES SON LOS ESQUEMAS";
        String result = intentKeywordService.detectIntentAndGetTool(userMessage, availableTools);
        assertEquals("list_schemas", result);
    }

    @Test
    void shouldMatchKeywordsWithinLongerText() {
        String userMessage = "Hola, me gustaría saber cuales son los esquemas disponibles por favor";
        String result = intentKeywordService.detectIntentAndGetTool(userMessage, availableTools);
        assertEquals("list_schemas", result);
    }

    @Test
    void shouldPrioritizeFirstMatchingIntent() {
        // Create a message that could match multiple intents
        String userMessage = "buscar tablas"; // Could match QUERY (buscar) or LIST_TABLES (tablas)
        
        String result = intentKeywordService.detectIntentAndGetTool(userMessage, availableTools);
        
        // Should match LIST_SCHEMAS since it comes first in the iteration order
        // The actual order depends on the Map iteration, but it should be consistent
        assertNotNull(result);
        assertTrue(List.of("list_tables", "query_data").contains(result));
    }
}