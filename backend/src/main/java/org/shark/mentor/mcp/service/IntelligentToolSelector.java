package org.shark.mentor.mcp.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.shark.mentor.mcp.config.PromptProperties;
import org.shark.mentor.mcp.model.McpServer;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * Intelligent tool selector that uses LLM to understand natural language requests
 * and map them to appropriate MCP tools. Now uses configurable prompts from application.yml
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IntelligentToolSelector {

    private final LlmService llmService;
    private final PromptProperties promptProperties;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Uses LLM to intelligently select the best tool based on natural language request
     */
    public String selectBestTool(String userMessage, List<Map<String, Object>> availableTools, McpServer server) {
        if (availableTools.isEmpty()) {
            log.warn("No tools available for selection on server: {}", server.getName());
            return null;
        }

        try {
            String toolsJson = objectMapper.writeValueAsString(availableTools);
            String systemPrompt = buildToolSelectionPrompt(toolsJson, server.getName());
            String response = llmService.generate(userMessage, systemPrompt);
            
            log.debug("LLM tool selection response: {}", response);
            return extractToolNameFromResponse(response, availableTools);
            
        } catch (Exception e) {
            log.error("Error in intelligent tool selection: {}", e.getMessage(), e);
            // Fallback to simple selection
            return fallbackToolSelection(userMessage, availableTools);
        }
    }

    /**
     * Uses LLM to extract tool arguments from natural language based on tool schema
     */
    public Map<String, Object> extractToolArguments(String userMessage, String toolName, 
                                                   Map<String, Object> toolSchema, McpServer server) {
        try {
            String schemaJson = toolSchema != null ? objectMapper.writeValueAsString(toolSchema) : "{}";
            String systemPrompt = buildArgumentExtractionPrompt(toolName, schemaJson, server.getName());
            String response = llmService.generate(userMessage, systemPrompt);
            
            log.debug("LLM argument extraction response: {}", response);
            return parseArgumentsFromResponse(response);
            
        } catch (Exception e) {
            log.error("Error in intelligent argument extraction: {}", e.getMessage(), e);
            return Map.of(); // Return empty map on error
        }
    }

    private String buildToolSelectionPrompt(String toolsJson, String serverName) {
        String template = promptProperties.getToolSelectionPrompt();
        if (template == null) {
            log.warn("Tool selection prompt template not found in configuration, using fallback");
            return buildFallbackToolSelectionPrompt(toolsJson, serverName);
        }
        
        String translationPatterns = promptProperties.buildTranslationPatterns();
        
        return template
                .replace("{serverName}", serverName)
                .replace("{toolsJson}", toolsJson)
                .replace("{translationPatterns}", translationPatterns);
    }

    private String buildArgumentExtractionPrompt(String toolName, String schemaJson, String serverName) {
        String template = promptProperties.getArgumentExtractionPrompt();
        if (template == null) {
            log.warn("Argument extraction prompt template not found in configuration, using fallback");
            return buildFallbackArgumentExtractionPrompt(toolName, schemaJson, serverName);
        }
        
        String translationMappings = promptProperties.buildTranslationMappings();
        
        return template
                .replace("{serverName}", serverName)
                .replace("{toolName}", toolName)
                .replace("{schemaJson}", schemaJson)
                .replace("{translationMappings}", translationMappings);
    }

    private String extractToolNameFromResponse(String response, List<Map<String, Object>> availableTools) {
        if (response == null || response.trim().isEmpty()) {
            return null;
        }
        
        String cleanResponse = response.trim();
        
        // Check if response is "NONE"
        if ("NONE".equalsIgnoreCase(cleanResponse)) {
            return null;
        }
        
        // Find exact match first
        for (Map<String, Object> tool : availableTools) {
            String toolName = (String) tool.get("name");
            if (toolName != null && toolName.equals(cleanResponse)) {
                log.info("LLM selected tool: {}", toolName);
                return toolName;
            }
        }
        
        // Try case-insensitive match
        for (Map<String, Object> tool : availableTools) {
            String toolName = (String) tool.get("name");
            if (toolName != null && toolName.equalsIgnoreCase(cleanResponse)) {
                log.info("LLM selected tool (case-insensitive): {}", toolName);
                return toolName;
            }
        }
        
        // Try partial match as last resort
        for (Map<String, Object> tool : availableTools) {
            String toolName = (String) tool.get("name");
            if (toolName != null && cleanResponse.contains(toolName)) {
                log.info("LLM selected tool (partial match): {}", toolName);
                return toolName;
            }
        }
        
        log.warn("LLM response '{}' did not match any available tool", cleanResponse);
        return null;
    }

    private Map<String, Object> parseArgumentsFromResponse(String response) {
        if (response == null || response.trim().isEmpty()) {
            return Map.of();
        }
        
        try {
            // Extract JSON from response (handle cases where LLM adds extra text)
            String jsonPart = extractJsonFromText(response.trim());
            if (jsonPart.isEmpty()) {
                return Map.of();
            }
            
            JsonNode jsonNode = objectMapper.readTree(jsonPart);
            if (jsonNode.isObject()) {
                return objectMapper.convertValue(jsonNode, Map.class);
            }
        } catch (Exception e) {
            log.warn("Failed to parse LLM argument response as JSON: {}", response, e);
        }
        
        return Map.of();
    }

    private String extractJsonFromText(String text) {
        // Try to find JSON object in the text
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        
        if (start != -1 && end != -1 && end > start) {
            return text.substring(start, end + 1);
        }
        
        // If no JSON brackets found, return the text as-is (might be empty object)
        if (text.equals("{}")) {
            return text;
        }
        
        return "";
    }

    private String fallbackToolSelection(String userMessage, List<Map<String, Object>> availableTools) {
        log.info("Using fallback tool selection for message: '{}'", userMessage);
        String lower = userMessage.toLowerCase();
        
        // Try exact name match first
        for (Map<String, Object> tool : availableTools) {
            Object nameObj = tool.get("name");
            if (nameObj instanceof String) {
                String name = ((String) nameObj).toLowerCase();
                if (lower.contains(name)) {
                    log.info("Fallback selected tool by name: {}", nameObj);
                    return (String) nameObj;
                }
            }
        }
        
        // Try description match
        for (Map<String, Object> tool : availableTools) {
            Object descObj = tool.get("description");
            if (descObj instanceof String) {
                String description = ((String) descObj).toLowerCase();
                for (String word : description.split("\\W+")) {
                    if (word.length() > 3 && lower.contains(word)) {
                        log.info("Fallback selected tool by description: {}", tool.get("name"));
                        return (String) tool.get("name");
                    }
                }
            }
        }
        
        // Last resort: return first tool
        String fallback = (String) availableTools.get(0).get("name");
        log.info("Fallback selected first available tool: {}", fallback);
        return fallback;
    }

    private String buildFallbackToolSelectionPrompt(String toolsJson, String serverName) {
        return String.format("""
            You are an intelligent tool selector for an MCP (Model Context Protocol) client with advanced natural language understanding.
            Your task is to analyze user requests in Spanish or English and intelligently select the most appropriate tool.
            
            SERVER: %s
            AVAILABLE TOOLS:
            %s
            
            CORE CAPABILITIES:
            1. **Spanish-English Translation Understanding**: Automatically understand Spanish terms and map them to English tool names
            2. **Semantic Intent Analysis**: Focus on what the user wants to accomplish, not just keyword matching
            3. **Context-Aware Selection**: Consider the relationship between user intent and tool functionality
            4. **Flexible Language Processing**: Handle variations, synonyms, and natural language patterns
            
            SPANISH TRANSLATION PATTERNS (understand these conceptually, don't just match keywords):
            - "esquemas" / "cuales son los esquemas" / "listar esquemas" → concepts related to "schemas" or "list_schemas"
            - "tablas" / "que tablas hay" / "mostrar tablas" → concepts related to "tables" or "list_tables"
            - "estructura" / "describir" / "formato" → concepts related to "describe" or schema information
            - "datos" / "consulta" / "buscar" / "filtrar" → concepts related to "query" or "search"
            - "ejemplos" / "muestra" / "sample" → concepts related to "sample" or example data
            - "repositorios" / "repos" → concepts related to "repositories"
            - "archivos" / "contenido" → concepts related to "files" or "contents"
            
            INTELLIGENT SELECTION PROCESS:
            1. Understand the user's intent regardless of exact wording
            2. Map Spanish concepts to English tool functionality
            3. Consider tool descriptions and purposes, not just names
            4. Prioritize tools that best accomplish the user's goal
            5. Use semantic understanding over literal matching
            
            RESPONSE FORMAT:
            Respond with ONLY the exact tool name (nothing else). If no tool is suitable, respond with "NONE".
            
            REASONING APPROACH:
            Instead of relying on examples, analyze:
            - What is the user trying to accomplish?
            - Which tool's description best matches that intent?
            - How do Spanish terms map to English concepts?
            - What would be the most logical tool for this task?
            """, serverName, toolsJson);
    }

    private String buildFallbackArgumentExtractionPrompt(String toolName, String schemaJson, String serverName) {
        return String.format("""
            You are an intelligent parameter extractor for MCP tool calls with advanced Spanish-English translation capabilities.
            Your task is to extract appropriate arguments from natural language requests for a specific tool.
            
            SERVER: %s
            TOOL: %s
            TOOL SCHEMA: %s
            
            CORE CAPABILITIES:
            1. **Spanish-English Translation**: Automatically understand and translate Spanish terms to appropriate English parameter values
            2. **Semantic Understanding**: Extract meaning and intent, not just literal words
            3. **Context-Aware Extraction**: Consider what the user is trying to accomplish
            4. **Flexible Parameter Mapping**: Map natural language to structured data types
            
            SPANISH TRANSLATION UNDERSTANDING:
            - "público" / "publico" → "public"
            - "privado" → "private"
            - "último mes" / "mes pasado" → time-related values or "last_month"
            - "esquemas" → "schemas" (for search terms or table names)
            - "tablas" → "tables" (for search terms or references)
            - "usuarios" / "clientes" / "productos" / "ventas" → table/entity names
            - Numbers in Spanish: "cinco" → 5, "diez" → 10, etc.
            
            INTELLIGENT EXTRACTION PROCESS:
            1. Understand the user's intent and what data they're requesting
            2. Map Spanish terms to their English equivalents when appropriate
            3. Extract parameters that match the tool's schema requirements
            4. Use semantic understanding to infer implicit parameters
            5. Convert natural language descriptions to appropriate data types
            6. Only include parameters that are clearly mentioned or strongly implied
            
            RESPONSE FORMAT:
            Respond with ONLY a valid JSON object containing the extracted parameters.
            Use empty object {} if no parameters can be extracted.
            
            REASONING APPROACH:
            Instead of pattern matching, analyze:
            - What specific data is the user requesting?
            - How do the Spanish terms map to the tool's expected parameters?
            - What would be the most logical parameter values for this request?
            - Are there implicit parameters based on the user's intent?
            """, serverName, toolName, schemaJson);
    }
}