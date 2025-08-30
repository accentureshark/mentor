package org.shark.mentor.mcp.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.shark.mentor.mcp.model.McpServer;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * Intelligent tool selector that uses LLM to understand natural language requests
 * and map them to appropriate MCP tools
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IntelligentToolSelector {

    private final LlmService llmService;
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
        return String.format("""
            You are an intelligent tool selector for an MCP (Model Context Protocol) client.
            Your task is to analyze a user's natural language request and select the most appropriate tool.
            
            SERVER: %s
            AVAILABLE TOOLS:
            %s
            
            INSTRUCTIONS:
            1. Analyze the user's intent and requirements
            2. Match the request to the most suitable tool based on:
               - Tool name relevance
               - Tool description matching the user's intent
               - Input parameters that can be satisfied by the request
            3. Consider synonyms and variations in language
            4. Think about the user's goal, not just keywords
            
            RESPONSE FORMAT:
            Respond with ONLY the exact tool name (nothing else). If no tool is suitable, respond with "NONE".
            
            EXAMPLES:
            - "Dame las ventas del último mes" -> query_data
            - "¿Qué tablas hay disponibles?" -> list_tables  
            - "Muéstrame la estructura de la tabla clientes" -> describe_table
            - "Buscar tablas relacionadas con ventas" -> search_tables
            - "Dame algunos datos de ejemplo de productos" -> sample_data
            - "¿Cuántas filas tiene la tabla usuarios?" -> count_rows
            - "Mostrar repositorios públicos" -> list_repositories
            - "Buscar repositorio con react" -> search_repositories
            """, serverName, toolsJson);
    }

    private String buildArgumentExtractionPrompt(String toolName, String schemaJson, String serverName) {
        return String.format("""
            You are an intelligent parameter extractor for MCP tool calls.
            Your task is to extract the appropriate arguments from a natural language request for a specific tool.
            
            SERVER: %s
            TOOL: %s
            TOOL SCHEMA: %s
            
            INSTRUCTIONS:
            1. Analyze the user's request to identify relevant parameters
            2. Extract values that match the tool's input schema
            3. Use intelligent mapping for natural language terms
            4. Handle synonyms and variations (e.g., "público" = "public", "último mes" = time ranges)
            5. Only include parameters that are clearly mentioned or strongly implied
            6. Convert natural language to appropriate data types (strings, numbers, booleans)
            
            RESPONSE FORMAT:
            Respond with ONLY a valid JSON object containing the extracted parameters.
            Use empty object {} if no parameters can be extracted.
            
            EXAMPLES:
            - "Dame las ventas del último mes" -> {"query": "ventas", "timeframe": "last_month"}
            - "SELECT * FROM productos LIMIT 10" -> {"query": "SELECT * FROM productos LIMIT 10"}
            - "Describe la tabla clientes" -> {"table": "clientes"}
            - "Dame 5 filas de usuarios" -> {"table": "usuarios", "limit": 5}
            - "repositorios públicos" -> {"type": "public"}
            - "buscar react en github" -> {"q": "react"}
            """, serverName, toolName, schemaJson);
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
}