package org.shark.mentor.mcp.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.shark.mentor.mcp.application.service.llm.LlmService;
import org.shark.mentor.mcp.application.service.tool.IntelligentToolSelector;
import org.shark.mentor.mcp.infraestructure.config.PromptProperties;
import org.shark.mentor.mcp.domain.model.McpServer;


import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class IntelligentToolSelectorConfigurationTest {

    @Mock
    private LlmService llmService;
    
    @Mock
    private PromptProperties promptProperties;

    private IntelligentToolSelector intelligentToolSelector;
    private McpServer testServer;
    private List<Map<String, Object>> dataLakeTools;

    @BeforeEach
    void setUp() {
        intelligentToolSelector = new IntelligentToolSelector(llmService, promptProperties);
        testServer = new McpServer("test-server", "Data Lake Server", "Data Lake MCP Server", "http://localhost:8080", "active");
        
        // Setup data lake tools similar to the problem statement
        dataLakeTools = List.of(
            Map.of("name", "query_data", "description", "Ejecuta una consulta en lenguaje natural o SQL sobre el data lake",
                   "inputSchema", Map.of("properties", Map.of("query", Map.of("type", "string"))))
        );
    }

    @Test
    void shouldUseConfigurablePromptTemplatesWhenAvailable() {
        // Given - Configure the mock to return a custom prompt template
        String customToolSelectionPrompt = "Custom tool selector for {serverName} with tools: {toolsJson} and patterns: {translationPatterns}";
        when(promptProperties.getToolSelectionPrompt()).thenReturn(customToolSelectionPrompt);
        when(promptProperties.buildTranslationPatterns()).thenReturn("- esquemas → schemas\n- tablas → tables\n");
        when(llmService.generate(anyString(), contains("Custom tool selector"))).thenReturn("query_data");

        // When
        String selectedTool = intelligentToolSelector.selectBestTool("Dame las ventas del último mes", dataLakeTools, testServer);

        // Then
        assertEquals("query_data", selectedTool);
        
        // Verify that the custom prompt template was used
        verify(llmService).generate(anyString(), contains("Custom tool selector for Data Lake Server"));
        verify(llmService).generate(anyString(), contains("esquemas → schemas"));
        verify(llmService).generate(anyString(), contains("tablas → tables"));
    }

    @Test
    void shouldUseConfigurableArgumentExtractionPromptWhenAvailable() {
        // Given - Configure the mock to return a custom argument extraction prompt
        String customArgumentPrompt = "Custom parameter extractor for {toolName} on {serverName} with schema {schemaJson} and mappings: {translationMappings}";
        when(promptProperties.getArgumentExtractionPrompt()).thenReturn(customArgumentPrompt);
        when(promptProperties.buildTranslationMappings()).thenReturn("- público → public\n- privado → private\n");
        when(llmService.generate(anyString(), contains("Custom parameter extractor"))).thenReturn("{\"query\": \"ventas del último mes\"}");

        // When
        Map<String, Object> arguments = intelligentToolSelector.extractToolArguments(
            "Dame las ventas del último mes", 
            "query_data", 
            Map.of("inputSchema", Map.of("properties", Map.of("query", Map.of("type", "string")))),
            testServer
        );

        // Then
        assertEquals("ventas del último mes", arguments.get("query"));
        
        // Verify that the custom argument extraction prompt was used
        verify(llmService).generate(anyString(), contains("Custom parameter extractor for query_data"));
        verify(llmService).generate(anyString(), contains("público → public"));
        verify(llmService).generate(anyString(), contains("privado → private"));
    }

    @Test
    void shouldFallbackWhenConfigurationIsNotAvailable() {
        // Given - Configure the mock to return null (no configuration)
        lenient().when(promptProperties.getToolSelectionPrompt()).thenReturn(null);
        lenient().when(promptProperties.getArgumentExtractionPrompt()).thenReturn(null);
        lenient().when(promptProperties.buildTranslationPatterns()).thenReturn("");
        lenient().when(promptProperties.buildTranslationMappings()).thenReturn("");
        when(llmService.generate(anyString(), contains("intelligent tool selector"))).thenReturn("query_data");

        // When
        String selectedTool = intelligentToolSelector.selectBestTool("Dame las ventas del último mes", dataLakeTools, testServer);

        // Then
        assertEquals("query_data", selectedTool);
        
        // Verify that the fallback prompt was used (contains the fallback text)
        verify(llmService).generate(anyString(), contains("You are an intelligent tool selector for an MCP"));
    }
}