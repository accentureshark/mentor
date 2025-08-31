package org.shark.mentor.mcp.application.service.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.shark.mentor.mcp.application.service.llm.LlmService;
import org.shark.mentor.mcp.infraestructure.config.PromptProperties;
import org.shark.mentor.mcp.domain.model.McpServer;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.HashMap;

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
     * with enhanced context awareness for dynamic tool discovery
     */
    public String selectBestTool(String userMessage, List<Map<String, Object>> availableTools, McpServer server) {
        if (availableTools.isEmpty()) {
            log.warn("No tools available for selection on server: {}", server.getName());
            return null;
        }

        try {
            String toolsJson = objectMapper.writeValueAsString(availableTools);
            String systemPrompt = buildEnhancedToolSelectionPrompt(toolsJson, server.getName(), userMessage);
            String response = llmService.generate(userMessage, systemPrompt);
            
            log.debug("LLM tool selection response for '{}': {}", userMessage, response);
            String selectedTool = extractToolNameFromResponse(response, availableTools);
            
            // If LLM selection failed or returned null, use enhanced fallback
            if (selectedTool == null) {
                log.info("LLM selection returned null, using enhanced fallback for message: '{}'", userMessage);
                return enhancedFallbackToolSelection(userMessage, availableTools, server);
            }
            
            return selectedTool;
            
        } catch (Exception e) {
            log.error("Error in intelligent tool selection: {}", e.getMessage(), e);
            // Fallback to enhanced selection
            return enhancedFallbackToolSelection(userMessage, availableTools, server);
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
        
        // Enhanced Spanish translation matching
        String selectedTool = trySpanishTranslationMatching(lower, availableTools);
        if (selectedTool != null) {
            log.info("Fallback selected tool by Spanish translation: {}", selectedTool);
            return selectedTool;
        }
        
        // Try exact name match
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
    
    private String trySpanishTranslationMatching(String lowerMessage, List<Map<String, Object>> availableTools) {
        // Check for schema-related Spanish terms
        if (containsAnySpanishTerm(lowerMessage, "esquemas", "cuales son los esquemas", "listar esquemas", "listame todos los esquemas", "todos los esquemas")) {
            return findToolByName(availableTools, "list_schemas");
        }
        
        // Check for table-related Spanish terms  
        if (containsAnySpanishTerm(lowerMessage, "tablas", "que tablas hay", "mostrar tablas", "listame las tablas", "todas las tablas")) {
            return findToolByName(availableTools, "list_tables");
        }
        
        // Check for describe/structure Spanish terms
        if (containsAnySpanishTerm(lowerMessage, "estructura", "describir", "formato", "describe")) {
            return findToolByName(availableTools, "describe_table");
        }
        
        // Check for query-related Spanish terms (including data/record requests)
        if (containsAnySpanishTerm(lowerMessage, "consulta", "buscar", "filtrar", "query", 
                                  "registros", "datos", "información", "contenido")) {
            return findToolByName(availableTools, "query_presto");
        }
        
        // Check for specific table data request patterns
        if (isTableDataRequestPattern(lowerMessage)) {
            return findToolByName(availableTools, "query_presto");
        }
        
        return null;
    }
    
    private boolean containsAnySpanishTerm(String message, String... terms) {
        for (String term : terms) {
            if (message.contains(term.toLowerCase())) {
                return true;
            }
        }
        return false;
    }
    
    /**
     * Detects specific table data request patterns in Spanish translation matching
     */
    private boolean isTableDataRequestPattern(String lowerMessage) {
        // "dame los registros de la tabla X"
        if (lowerMessage.contains("dame") && lowerMessage.contains("registros") && lowerMessage.contains("tabla")) {
            return true;
        }
        
        // "muéstrame los datos de la tabla X"
        if (lowerMessage.contains("muestra") && lowerMessage.contains("datos") && lowerMessage.contains("tabla")) {
            return true;
        }
        
        // Other data request patterns
        return (lowerMessage.contains("tabla") && 
                (lowerMessage.contains("registros") || lowerMessage.contains("datos") || 
                 lowerMessage.contains("información") || lowerMessage.contains("contenido")));
    }
    
    private String findToolByName(List<Map<String, Object>> availableTools, String toolName) {
        for (Map<String, Object> tool : availableTools) {
            Object nameObj = tool.get("name");
            if (nameObj instanceof String && toolName.equals((String) nameObj)) {
                return (String) nameObj;
            }
        }
        return null;
    }

    private String buildEnhancedToolSelectionPrompt(String toolsJson, String serverName, String userMessage) {
        return String.format("""
            You are an advanced intelligent tool selector for an MCP (Model Context Protocol) client with sophisticated natural language understanding.
            Your primary goal is to understand user intent and select the most appropriate tool from available options.
            
            SERVER: %s
            USER REQUEST: %s
            AVAILABLE TOOLS:
            %s
            
            ENHANCED CAPABILITIES:
            1. **Intent Analysis**: Deeply understand what the user is trying to accomplish
            2. **Semantic Matching**: Match user goals with tool capabilities, not just keywords
            3. **Context Awareness**: Consider the server type and available tool ecosystem
            4. **Multi-Language Support**: Handle Spanish, English, and mixed language requests seamlessly
            5. **Dynamic Adaptation**: Work with any MCP server without hardcoded patterns
            
            INTELLIGENT SELECTION STRATEGY:
            1. **Analyze User Intent**: What is the user trying to achieve?
               - Data retrieval? (list, show, get)
               - Information query? (search, find, filter)
               - Structural exploration? (describe, explain, structure)
               - Management operations? (create, update, delete)
            
            2. **Map Intent to Capabilities**: 
               - For "list/show/get all" → tools with "list", "get", "all" concepts
               - For "search/find/filter" → tools with "search", "query", "filter" concepts
               - For "describe/explain/structure" → tools with "describe", "schema", "info" concepts
               - For specific entities (branches, files, issues) → tools containing those entity names
            
            3. **Language Translation Understanding**:
               - "branches/ramas" → branch-related tools
               - "archivos/files" → file-related tools
               - "repositorios/repos" → repository-related tools
               - "issues/problemas" → issue-related tools
               - "listar/mostrar/ver" → list/show/get operations
               - "buscar/encontrar" → search/find operations
            
            4. **Contextual Reasoning**:
               - Consider tool descriptions alongside names
               - Prioritize tools that best match the user's specific need
               - If multiple tools could work, choose the most specific one
               - Prefer simpler operations when the intent is unclear
            
            RESPONSE PROTOCOL:
            - Respond with ONLY the exact tool name (nothing else)
            - If no tool is appropriate, respond with "NONE"
            - Do not explain your reasoning in the response
            
            EXAMPLE REASONING (do not include in response):
            - "show me all branches" → look for tools with "list" + "branch" concepts
            - "dame todos los repositorios" → look for tools with "list" + "repo" concepts
            - "buscar archivos" → look for tools with "search" + "file" concepts
            
            Your task: Analyze the user request and select the single best tool that accomplishes their goal.
            """, serverName, userMessage, toolsJson);
    }
    
    private String enhancedFallbackToolSelection(String userMessage, List<Map<String, Object>> availableTools, McpServer server) {
        log.info("Using enhanced fallback tool selection for message: '{}' on server: {}", userMessage, server.getName());
        
        String lower = userMessage.toLowerCase().trim();
        
        // Enhanced intent-based matching
        String selectedTool = analyzeIntentAndSelectTool(lower, availableTools);
        if (selectedTool != null) {
            log.info("Enhanced fallback selected tool by intent analysis: {}", selectedTool);
            return selectedTool;
        }
        
        // Enhanced semantic matching
        selectedTool = performSemanticToolMatching(lower, availableTools);
        if (selectedTool != null) {
            log.info("Enhanced fallback selected tool by semantic matching: {}", selectedTool);
            return selectedTool;
        }
        
        // Original fallback logic as last resort
        return fallbackToolSelection(userMessage, availableTools);
    }
    
    private String analyzeIntentAndSelectTool(String lowerMessage, List<Map<String, Object>> availableTools) {
        // Detect primary intent
        Intent intent = detectPrimaryIntent(lowerMessage);
        String entityType = extractEntityType(lowerMessage);
        
        // Score all tools and find the best match
        Map<String, Integer> toolScores = new HashMap<>();
        
        for (Map<String, Object> tool : availableTools) {
            String toolName = (String) tool.get("name");
            String description = (String) tool.get("description");
            
            int score = scoreToolMatch(toolName, description, intent, entityType, lowerMessage);
            if (score > 0) {
                toolScores.put(toolName, score);
            }
        }
        
        // Return the tool with the highest score
        return toolScores.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(null);
    }
    
    private int scoreToolMatch(String toolName, String description, Intent intent, String entityType, String userMessage) {
        if (toolName == null) return 0;
        
        String lowerToolName = toolName.toLowerCase();
        String lowerDescription = description != null ? description.toLowerCase() : "";
        
        int score = 0;
        
        // Score entity match in tool name (high priority)
        boolean entityInName = false;
        if (!"unknown".equals(entityType)) {
            entityInName = switch (entityType) {
                case "branch" -> containsAny(lowerToolName, "branch", "branches");
                case "repository" -> containsAny(lowerToolName, "repo", "repository", "repositories");
                case "file" -> containsAny(lowerToolName, "file", "files", "content", "contents");
                case "issue" -> containsAny(lowerToolName, "issue", "issues");
                case "pull_request" -> containsAny(lowerToolName, "pull", "pr", "merge");
                case "commit" -> containsAny(lowerToolName, "commit", "commits");
                case "table" -> containsAny(lowerToolName, "table", "tables");
                case "schema" -> containsAny(lowerToolName, "schema", "schemas");
                case "toolset" -> containsAny(lowerToolName, "toolset", "toolsets");
                default -> false;
            };
            if (entityInName) {
                score += 50; // High score for entity match in name
            }
        }
        
        // Score intent match in tool name (HIGH priority for QUERY intent)
        boolean intentInName = switch (intent) {
            case LIST -> containsAny(lowerToolName, "list", "get", "show");
            case SEARCH -> containsAny(lowerToolName, "search", "find", "filter", "query");
            case DESCRIBE -> containsAny(lowerToolName, "describe", "info", "detail", "schema", "structure");
            case QUERY -> containsAny(lowerToolName, "query", "execute", "run", "sql");
            case UNKNOWN -> false;
        };
        if (intentInName) {
            // Give higher priority to QUERY intent when it's a data request
            int intentScore = (intent == Intent.QUERY) ? 60 : 30; // Higher score for query intent
            score += intentScore;
        }
        
        // Bonus for specific user words appearing in tool name
        for (String word : userMessage.split("\\s+")) {
            if (word.length() > 2 && lowerToolName.contains(word)) {
                score += 10; // Bonus for each user word in tool name
            }
        }
        
        // Score "available" keyword match for toolsets (special case)
        if ("toolset".equals(entityType) && userMessage.contains("available") && lowerToolName.contains("available")) {
            score += 20; // Special bonus for available toolsets
        }
        
        // Must have at least entity or intent match to be considered
        if (!entityInName && !intentInName) {
            return 0;
        }
        
        return score;
    }
    
    private boolean toolMatchesIntentAndEntityInName(String toolName, Intent intent, String entityType) {
        if (toolName == null) return false;
        
        String lowerToolName = toolName.toLowerCase();
        
        // Check entity match in tool name only (more precise)
        boolean entityMatch = false;
        if ("unknown".equals(entityType)) {
            entityMatch = true; // If entity is unknown, any tool could match
        } else {
            entityMatch = switch (entityType) {
                case "branch" -> containsAny(lowerToolName, "branch", "branches");
                case "repository" -> containsAny(lowerToolName, "repo", "repository", "repositories");
                case "file" -> containsAny(lowerToolName, "file", "files", "content", "contents");
                case "issue" -> containsAny(lowerToolName, "issue", "issues");
                case "pull_request" -> containsAny(lowerToolName, "pull", "pr", "merge");
                case "commit" -> containsAny(lowerToolName, "commit", "commits");
                case "table" -> containsAny(lowerToolName, "table", "tables");
                case "schema" -> containsAny(lowerToolName, "schema", "schemas");
                case "toolset" -> containsAny(lowerToolName, "toolset", "toolsets");
                default -> false;
            };
        }
        
        if (!entityMatch) {
            return false;
        }
        
        // Check intent match in tool name with priority scoring
        boolean intentMatch = switch (intent) {
            case LIST -> {
                // Prefer tools with explicit list operations
                if (containsAny(lowerToolName, "list", "get", "show")) {
                    yield true;
                }
                // Accept tools with "available" for list intent on toolsets
                if ("toolset".equals(entityType) && lowerToolName.contains("available")) {
                    yield true;
                }
                yield false;
            }
            case SEARCH -> containsAny(lowerToolName, "search", "find", "filter", "query");
            case DESCRIBE -> containsAny(lowerToolName, "describe", "info", "detail", "schema", "structure");
            case QUERY -> containsAny(lowerToolName, "query", "execute", "run", "sql");
            case UNKNOWN -> true; // If intent is unknown, any entity match in name is good
        };
        
        return intentMatch;
    }
    
    private Intent detectPrimaryIntent(String message) {
        // PRIORITY 1: Data/Query intent - specific patterns for table data requests
        if (isTableDataRequest(message)) {
            return Intent.QUERY;
        }
        
        // PRIORITY 2: Query/Data intent - general query terms
        if (containsAny(message, "query", "consulta", "data", "datos", "sql")) {
            return Intent.QUERY;
        }
        
        // PRIORITY 3: Search/Find intent
        if (containsAny(message, "search", "buscar", "find", "encontrar", "filter", "filtrar")) {
            return Intent.SEARCH;
        }
        
        // PRIORITY 4: Describe/Info intent
        if (containsAny(message, "describe", "describir", "info", "structure", "estructura", "schema", "explain")) {
            return Intent.DESCRIBE;
        }
        
        // PRIORITY 5: List/Show/Get intent (lowest priority to avoid conflicts)
        if (containsAny(message, "list", "listar", "show", "mostrar", "get", "todos", "all", "ver", "dame", "what", "available")) {
            return Intent.LIST;
        }
        
        return Intent.UNKNOWN;
    }
    
    /**
     * Detects if the message is specifically requesting table data/records
     */
    private boolean isTableDataRequest(String message) {
        String lower = message.toLowerCase();
        
        // Pattern 1: "registros de la tabla X" (records from table X)
        if (containsAny(lower, "registros") && containsAny(lower, "tabla")) {
            return true;
        }
        
        // Pattern 2: "datos de la tabla X" (data from table X)  
        if (containsAny(lower, "datos") && containsAny(lower, "tabla")) {
            return true;
        }
        
        // Pattern 3: "información de la tabla X" (information from table X)
        if (containsAny(lower, "información") && containsAny(lower, "tabla")) {
            return true;
        }
        
        // Pattern 4: "contenido de la tabla X" (content from table X)
        if (containsAny(lower, "contenido") && containsAny(lower, "tabla")) {
            return true;
        }
        
        // Pattern 5: "dame X de la tabla Y" where X is data-related
        if (containsAny(lower, "dame") && containsAny(lower, "tabla") && 
            containsAny(lower, "registros", "datos", "información", "contenido", "todo", "toda")) {
            return true;
        }
        
        // Pattern 6: "mostrar/ver datos/registros" (show data/records)
        if (containsAny(lower, "mostrar", "ver", "muestra", "muéstrame") && 
            containsAny(lower, "datos", "registros", "información", "contenido")) {
            return true;
        }
        
        // Pattern 7: "necesito/quiero X de tabla Y" (I need/want X from table Y)
        if (containsAny(lower, "necesito", "quiero") && containsAny(lower, "tabla") &&
            containsAny(lower, "datos", "registros", "información")) {
            return true;
        }
        
        return false;
    }
    
    private String extractEntityType(String message) {
        if (containsAny(message, "branch", "branches", "rama", "ramas")) {
            return "branch";
        }
        if (containsAny(message, "repositories", "repository", "repositorio", "repositorios", "repos")) {
            return "repository";
        }
        if (containsAny(message, "file", "files", "archivo", "archivos", "content", "contenido")) {
            return "file";
        }
        if (containsAny(message, "issue", "issues", "problema", "problemas")) {
            return "issue";
        }
        if (containsAny(message, "pull", "pr", "merge")) {
            return "pull_request";
        }
        if (containsAny(message, "commit", "commits")) {
            return "commit";
        }
        if (containsAny(message, "table", "tables", "tabla", "tablas")) {
            return "table";
        }
        if (containsAny(message, "schema", "schemas", "esquema", "esquemas")) {
            return "schema";
        }
        if (containsAny(message, "toolset", "toolsets")) {
            return "toolset";
        }
        // Check for "repo" as a separate word or short form  
        if (message.matches(".*\\brepo\\b.*") || containsAny(message, " repo ", "repo ")) {
            return "repository";
        }
        
        return "unknown";
    }
    
    private boolean toolMatchesIntentAndEntity(String toolName, String description, Intent intent, String entityType) {
        if (toolName == null) return false;
        
        String lowerToolName = toolName.toLowerCase();
        String lowerDescription = description != null ? description.toLowerCase() : "";
        String combinedText = lowerToolName + " " + lowerDescription;
        
        // Check entity match first - this is the most important
        boolean entityMatch = false;
        if ("unknown".equals(entityType)) {
            entityMatch = true; // If entity is unknown, any tool could match
        } else {
            entityMatch = switch (entityType) {
                case "branch" -> containsAny(combinedText, "branch", "branches");
                case "repository" -> containsAny(combinedText, "repo", "repository", "repositories");
                case "file" -> containsAny(combinedText, "file", "files", "content", "contents");
                case "issue" -> containsAny(combinedText, "issue", "issues");
                case "pull_request" -> containsAny(combinedText, "pull", "pr", "merge");
                case "commit" -> containsAny(combinedText, "commit", "commits");
                case "table" -> containsAny(combinedText, "table", "tables");
                case "schema" -> containsAny(combinedText, "schema", "schemas");
                case "toolset" -> containsAny(combinedText, "toolset", "toolsets", "available");
                default -> false;
            };
        }
        
        if (!entityMatch) {
            return false;
        }
        
        // Check intent match
        return switch (intent) {
            case LIST -> containsAny(combinedText, "list", "get", "all", "show");
            case SEARCH -> containsAny(combinedText, "search", "find", "filter", "query");
            case DESCRIBE -> containsAny(combinedText, "describe", "info", "detail", "schema", "structure");
            case QUERY -> containsAny(combinedText, "query", "execute", "run", "sql");
            case UNKNOWN -> true; // If intent is unknown, any entity match is good
        };
    }
    
    private boolean containsEntityVariations(String text, String entityType) {
        return switch (entityType) {
            case "branch" -> containsAny(text, "branches", "git");
            case "repository" -> containsAny(text, "repo", "repos", "git");
            case "file" -> containsAny(text, "files", "content", "blob");
            case "issue" -> containsAny(text, "issues", "bug", "ticket");
            case "pull_request" -> containsAny(text, "pull", "pr", "merge", "review");
            case "commit" -> containsAny(text, "commits", "git", "history");
            case "table" -> containsAny(text, "tables", "data");
            case "schema" -> containsAny(text, "schemas", "structure", "metadata");
            default -> false;
        };
    }
    
    private String performSemanticToolMatching(String lowerMessage, List<Map<String, Object>> availableTools) {
        Map<String, Integer> toolScores = new HashMap<>();
        
        for (Map<String, Object> tool : availableTools) {
            String toolName = (String) tool.get("name");
            String description = (String) tool.get("description");
            
            int score = calculateSemanticScore(lowerMessage, toolName, description);
            if (score > 0) {
                toolScores.put(toolName, score);
            }
        }
        
        // Return the tool with the highest score
        return toolScores.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(null);
    }
    
    private int calculateSemanticScore(String message, String toolName, String description) {
        int score = 0;
        
        if (toolName == null) return 0;
        
        String lowerToolName = toolName.toLowerCase();
        String lowerDescription = description != null ? description.toLowerCase() : "";
        
        // Exact word matches in tool name (highest priority)
        for (String word : message.split("\\s+")) {
            if (word.length() > 2 && lowerToolName.contains(word)) {
                score += 5;
            }
        }
        
        // Tool name contains message words
        for (String word : lowerToolName.split("_")) {
            if (word.length() > 2 && message.contains(word)) {
                score += 3;
            }
        }
        
        // Description matches
        for (String word : message.split("\\s+")) {
            if (word.length() > 3 && lowerDescription.contains(word)) {
                score += 1;
            }
        }
        
        // Bonus for complete concept matches
        if (containsCompleteConceptMatch(message, lowerToolName, lowerDescription)) {
            score += 10;
        }
        
        return score;
    }
    
    private boolean containsCompleteConceptMatch(String message, String toolName, String description) {
        String combined = toolName + " " + description;
        
        // Check for complete concept matches
        if (message.contains("list") && toolName.startsWith("list")) return true;
        if (message.contains("search") && toolName.contains("search")) return true;
        if (message.contains("get") && toolName.startsWith("get")) return true;
        if (message.contains("describe") && toolName.contains("describe")) return true;
        
        // Spanish equivalents
        if (containsAny(message, "listar", "mostrar") && toolName.startsWith("list")) return true;
        if (containsAny(message, "buscar", "encontrar") && toolName.contains("search")) return true;
        if (containsAny(message, "describir", "estructura") && toolName.contains("describe")) return true;
        
        return false;
    }
    
    private boolean containsAny(String text, String... words) {
        for (String word : words) {
            if (text.contains(word)) {
                return true;
            }
        }
        return false;
    }
    
    private enum Intent {
        LIST, SEARCH, DESCRIBE, QUERY, UNKNOWN
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
