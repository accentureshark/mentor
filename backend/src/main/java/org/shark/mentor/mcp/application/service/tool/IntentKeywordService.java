package org.shark.mentor.mcp.application.service.tool;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.shark.mentor.mcp.infraestructure.config.IntentKeywordProperties;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Service to handle intent detection based on keywords loaded from YAML configuration
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IntentKeywordService {

    private final IntentKeywordProperties intentKeywordProperties;

    /**
     * Detects the intent from a user message and returns the appropriate tool name
     *
     * @param userMessage The user's message to analyze
     * @param availableTools List of available tools to check against
     * @return The tool name that matches the detected intent, or null if no match
     */
    public String detectIntentAndGetTool(String userMessage, List<Map<String, Object>> availableTools) {
        if (userMessage == null || userMessage.trim().isEmpty()) {
            return null;
        }

        String lowerMessage = userMessage.toLowerCase();
        log.debug("Detecting intent for message: '{}'", userMessage);

        // Check each intent for keyword matches
        Map<String, IntentKeywordProperties.IntentDefinition> allIntents = intentKeywordProperties.getAllIntents();
        
        for (Map.Entry<String, IntentKeywordProperties.IntentDefinition> entry : allIntents.entrySet()) {
            String intentName = entry.getKey();
            IntentKeywordProperties.IntentDefinition intent = entry.getValue();
            
            if (intent != null && intent.getKeywords() != null) {
                boolean matchFound = false;
                
                // Check Spanish keywords
                if (intent.getKeywords().getSpanish() != null) {
                    matchFound = containsAnyKeyword(lowerMessage, intent.getKeywords().getSpanish());
                }
                
                // Check English keywords if no Spanish match
                if (!matchFound && intent.getKeywords().getEnglish() != null) {
                    matchFound = containsAnyKeyword(lowerMessage, intent.getKeywords().getEnglish());
                }
                
                if (matchFound) {
                    log.debug("Intent '{}' detected for message", intentName);
                    
                    // Find the first available tool that matches this intent
                    Optional<String> toolName = findFirstAvailableTool(intent.getToolNames(), availableTools);
                    if (toolName.isPresent()) {
                        log.info("Intent '{}' matched to tool: {}", intentName, toolName.get());
                        return toolName.get();
                    } else {
                        log.warn("Intent '{}' detected but no matching tools available: {}", intentName, intent.getToolNames());
                    }
                }
            }
        }

        log.debug("No intent detected for message: '{}'", userMessage);
        return null;
    }

    /**
     * Checks if a message contains any of the specified keywords
     */
    private boolean containsAnyKeyword(String message, List<String> keywords) {
        if (keywords == null || keywords.isEmpty()) {
            return false;
        }
        
        for (String keyword : keywords) {
            if (keyword != null && message.contains(keyword.toLowerCase())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Finds the first tool from the intent's tool names that exists in available tools
     */
    private Optional<String> findFirstAvailableTool(List<String> intentToolNames, List<Map<String, Object>> availableTools) {
        if (intentToolNames == null || intentToolNames.isEmpty()) {
            return Optional.empty();
        }

        for (String intentToolName : intentToolNames) {
            for (Map<String, Object> tool : availableTools) {
                Object nameObj = tool.get("name");
                if (nameObj instanceof String && intentToolName.equals((String) nameObj)) {
                    return Optional.of((String) nameObj);
                }
            }
        }
        
        return Optional.empty();
    }

    /**
     * Checks if a response matches schema listing patterns
     * Used for response analysis in ChatService
     */
    public boolean isSchemaResponse(String jsonString, String userMessage) {
        if (intentKeywordProperties.getPatterns() == null || 
            intentKeywordProperties.getPatterns().getSchema_response() == null) {
            return false;
        }

        IntentKeywordProperties.PatternDefinition schemaPattern = intentKeywordProperties.getPatterns().getSchema_response();
        String lowerJsonString = jsonString != null ? jsonString.toLowerCase() : "";
        String lowerUserMessage = userMessage != null ? userMessage.toLowerCase() : "";

        // Check if user message contains schema request keywords
        boolean userAskedForSchema = false;
        if (schemaPattern.getKeywords() != null) {
            if (schemaPattern.getKeywords().getSpanish() != null) {
                userAskedForSchema = containsAllKeywords(lowerUserMessage, schemaPattern.getKeywords().getSpanish());
            }
            if (!userAskedForSchema && schemaPattern.getKeywords().getEnglish() != null) {
                userAskedForSchema = containsAllKeywords(lowerUserMessage, schemaPattern.getKeywords().getEnglish());
            }
        }

        // Check if response contains schema-related content
        boolean responseContainsSchema = false;
        if (schemaPattern.getCombined_checks() != null) {
            responseContainsSchema = containsAnyKeyword(lowerJsonString, schemaPattern.getCombined_checks());
        }

        return userAskedForSchema || responseContainsSchema;
    }

    /**
     * Checks if a message contains all specified keywords
     */
    private boolean containsAllKeywords(String message, List<String> keywords) {
        if (keywords == null || keywords.isEmpty()) {
            return false;
        }
        
        for (String keyword : keywords) {
            if (keyword != null && !message.contains(keyword.toLowerCase())) {
                return false;
            }
        }
        return true;
    }

    /**
     * Gets the default tool selection order for fallback scenarios
     */
    public List<String> getDefaultToolSelectionOrder() {
        if (intentKeywordProperties.getFallback() != null && 
            intentKeywordProperties.getFallback().getDefault_tool_selection_order() != null) {
            return intentKeywordProperties.getFallback().getDefault_tool_selection_order();
        }
        return List.of(); // Empty list as fallback
    }
}