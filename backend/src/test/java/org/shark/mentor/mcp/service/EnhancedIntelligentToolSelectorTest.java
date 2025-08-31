package org.shark.mentor.mcp.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.shark.mentor.mcp.application.service.llm.LlmService;
import org.shark.mentor.mcp.application.service.tool.IntelligentToolSelector;
import org.shark.mentor.mcp.domain.model.McpServer;
import org.shark.mentor.mcp.infraestructure.config.PromptProperties;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EnhancedIntelligentToolSelectorTest {

    @Mock
    private LlmService llmService;
    
    @Mock
    private PromptProperties promptProperties;

    private IntelligentToolSelector intelligentToolSelector;
    private McpServer testServer;
    private List<Map<String, Object>> githubTools;

    @BeforeEach
    void setUp() {
        // Setup lenient mock prompt properties
        lenient().when(promptProperties.getToolSelectionPrompt()).thenReturn(null); // Will trigger fallback
        lenient().when(promptProperties.getArgumentExtractionPrompt()).thenReturn(null); // Will trigger fallback
        lenient().when(promptProperties.buildTranslationPatterns()).thenReturn("");
        lenient().when(promptProperties.buildTranslationMappings()).thenReturn("");
        
        intelligentToolSelector = new IntelligentToolSelector(llmService, promptProperties);
        testServer = new McpServer("github-server", "GitHub MCP Server", "GitHub integration server", "http://localhost:8080", "active");
        
        // Setup GitHub-like tools from the problem statement
        githubTools = List.of(
            Map.of("name", "enable_toolset", "description", "Enable one of the sets of tools the GitHub MCP server provides"),
            Map.of("name", "get_toolset_tools", "description", "Lists all the capabilities that are enabled with the specified toolset"),
            Map.of("name", "list_available_toolsets", "description", "List all available toolsets this GitHub MCP server can offer"),
            Map.of("name", "list_branches", "description", "List all branches in a repository"),
            Map.of("name", "list_repositories", "description", "List user repositories"),
            Map.of("name", "get_file_contents", "description", "Get the contents of a file from a repository"),
            Map.of("name", "search_repositories", "description", "Search for repositories"),
            Map.of("name", "list_issues", "description", "List issues in a repository")
        );
    }

    @Test
    void shouldSelectListBranchesForBranchRequest() {
        // Given
        String userMessage = "show me all branches";
        when(llmService.generate(anyString(), anyString())).thenReturn("list_branches");

        // When
        String selectedTool = intelligentToolSelector.selectBestTool(userMessage, githubTools, testServer);

        // Then
        assertEquals("list_branches", selectedTool);
    }

    @Test
    void shouldSelectListBranchesForSpanishBranchRequest() {
        // Given
        String userMessage = "dame todos los branches";
        when(llmService.generate(anyString(), anyString())).thenReturn("list_branches");

        // When
        String selectedTool = intelligentToolSelector.selectBestTool(userMessage, githubTools, testServer);

        // Then
        assertEquals("list_branches", selectedTool);
    }

    @Test
    void shouldFallbackToEnhancedSelectionWhenLlmFails() {
        // Given
        String userMessage = "list all repositories";
        when(llmService.generate(anyString(), anyString())).thenThrow(new RuntimeException("LLM error"));

        // When
        String selectedTool = intelligentToolSelector.selectBestTool(userMessage, githubTools, testServer);

        // Then
        assertEquals("list_repositories", selectedTool);
    }

    @Test
    void shouldSelectRepositoryToolForRepositoryIntent() {
        // Given
        String userMessage = "show me repositories";
        when(llmService.generate(anyString(), anyString())).thenReturn("NONE"); // LLM returns no match

        // When
        String selectedTool = intelligentToolSelector.selectBestTool(userMessage, githubTools, testServer);

        // Then
        assertEquals("list_repositories", selectedTool);
    }

    @Test
    void shouldSelectSearchToolForSearchIntent() {
        // Given
        String userMessage = "buscar repositorios con python";
        when(llmService.generate(anyString(), anyString())).thenReturn("NONE"); // LLM returns no match

        // When
        String selectedTool = intelligentToolSelector.selectBestTool(userMessage, githubTools, testServer);

        // Then
        assertEquals("search_repositories", selectedTool);
    }

    @Test
    void shouldSelectFileToolForFileIntent() {
        // Given
        String userMessage = "get file content from repo";
        when(llmService.generate(anyString(), anyString())).thenReturn("NONE"); // LLM returns no match

        // When
        String selectedTool = intelligentToolSelector.selectBestTool(userMessage, githubTools, testServer);

        // Then
        assertEquals("get_file_contents", selectedTool);
    }

    @Test
    void shouldSelectIssuesToolForIssueIntent() {
        // Given
        String userMessage = "mostrar issues del proyecto";
        when(llmService.generate(anyString(), anyString())).thenReturn("NONE"); // LLM returns no match

        // When
        String selectedTool = intelligentToolSelector.selectBestTool(userMessage, githubTools, testServer);

        // Then
        assertEquals("list_issues", selectedTool);
    }

    @Test
    void shouldHandleVariousSpanishRequests() {
        // Given
        String[] spanishQueries = {
            "listar todas las ramas",
            "mostrar repositorios",
            "buscar archivos",
            "ver problemas"
        };
        
        String[] expectedTools = {
            "list_branches",
            "list_repositories", 
            "get_file_contents", // "buscar archivos" -> "file" entity is strongly matched by get_file_contents  
            "list_issues"
        };
        
        when(llmService.generate(anyString(), anyString())).thenReturn("NONE"); // Force fallback

        for (int i = 0; i < spanishQueries.length; i++) {
            // When
            String selectedTool = intelligentToolSelector.selectBestTool(spanishQueries[i], githubTools, testServer);

            // Then
            assertEquals(expectedTools[i], selectedTool, 
                "Failed for query: " + spanishQueries[i] + ", expected: " + expectedTools[i] + ", got: " + selectedTool);
        }
    }

    @Test
    void shouldPrioritizeExactMatches() {
        // Given
        String userMessage = "enable github toolset";
        when(llmService.generate(anyString(), anyString())).thenReturn("NONE"); // Force fallback

        // When
        String selectedTool = intelligentToolSelector.selectBestTool(userMessage, githubTools, testServer);

        // Then
        assertEquals("enable_toolset", selectedTool);
    }

    @Test
    void shouldHandleComplexNaturalLanguageRequests() {
        // Given
        String userMessage = "I need to see what branches are available in the repository";
        when(llmService.generate(anyString(), anyString())).thenReturn("NONE"); // Force fallback

        // When
        String selectedTool = intelligentToolSelector.selectBestTool(userMessage, githubTools, testServer);

        // Then
        assertEquals("list_branches", selectedTool);
    }

    @Test
    void shouldSelectToolsetManagementForMetaRequests() {
        // Given
        String userMessage = "what toolsets are available?";
        when(llmService.generate(anyString(), anyString())).thenReturn("NONE"); // Force fallback

        // When
        String selectedTool = intelligentToolSelector.selectBestTool(userMessage, githubTools, testServer);

        // Then
        assertEquals("list_available_toolsets", selectedTool);
    }

    @Test
    void shouldReturnNullForEmptyToolsList() {
        // Given
        String userMessage = "show me branches";
        List<Map<String, Object>> emptyTools = List.of();

        // When
        String selectedTool = intelligentToolSelector.selectBestTool(userMessage, emptyTools, testServer);

        // Then
        assertNull(selectedTool);
    }

    @Test
    void shouldHandleMixedLanguageRequests() {
        // Given
        String userMessage = "list all branches por favor";
        when(llmService.generate(anyString(), anyString())).thenReturn("NONE"); // Force fallback

        // When
        String selectedTool = intelligentToolSelector.selectBestTool(userMessage, githubTools, testServer);

        // Then
        assertEquals("list_branches", selectedTool);
    }

    @Test
    void shouldExtractArgumentsIntelligently() {
        // Given
        String userMessage = "list branches for accentureshark/mentor repository";
        String toolName = "list_branches";
        Map<String, Object> toolSchema = Map.of(
            "properties", Map.of(
                "owner", Map.of("type", "string"),
                "repo", Map.of("type", "string")
            )
        );
        when(llmService.generate(anyString(), anyString())).thenReturn("{\"owner\":\"accentureshark\",\"repo\":\"mentor\"}");

        // When
        Map<String, Object> arguments = intelligentToolSelector.extractToolArguments(userMessage, toolName, toolSchema, testServer);

        // Then
        assertFalse(arguments.isEmpty());
        assertEquals("accentureshark", arguments.get("owner"));
        assertEquals("mentor", arguments.get("repo"));
    }

    @Test
    void shouldHandleArgumentExtractionFailure() {
        // Given
        String userMessage = "list branches";
        String toolName = "list_branches";
        Map<String, Object> toolSchema = Map.of("properties", Map.of());
        when(llmService.generate(anyString(), anyString())).thenThrow(new RuntimeException("LLM error"));

        // When
        Map<String, Object> arguments = intelligentToolSelector.extractToolArguments(userMessage, toolName, toolSchema, testServer);

        // Then
        assertTrue(arguments.isEmpty());
    }
}