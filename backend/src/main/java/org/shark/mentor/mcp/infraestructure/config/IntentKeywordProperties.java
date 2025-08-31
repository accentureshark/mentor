package org.shark.mentor.mcp.infraestructure.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Configuration properties for intent keywords loaded from application.yml
 */
@Data
@Component
@ConfigurationProperties(prefix = "intent.keywords")
public class IntentKeywordProperties {

    private IntentConfig intents;
    private PatternConfig patterns;
    private FallbackConfig fallback;

    @Data
    public static class IntentConfig {
        private IntentDefinition LIST_SCHEMAS;
        private IntentDefinition LIST_TABLES;
        private IntentDefinition DESCRIBE;
        private IntentDefinition QUERY;
        private IntentDefinition SAMPLE;
        private IntentDefinition SEARCH;
        private IntentDefinition REPOSITORIES;
        private IntentDefinition FILES;
    }

    @Data
    public static class IntentDefinition {
        private KeywordSet keywords;
        private List<String> toolNames;
        private String description;
    }

    @Data
    public static class KeywordSet {
        private List<String> spanish;
        private List<String> english;
    }

    @Data
    public static class PatternConfig {
        private PatternDefinition schema_response;
    }

    @Data
    public static class PatternDefinition {
        private KeywordSet keywords;
        private List<String> combined_checks;
        private String description;
    }

    @Data
    public static class FallbackConfig {
        private List<String> default_tool_selection_order;
    }

    /**
     * Get all intent definitions as a map
     */
    public Map<String, IntentDefinition> getAllIntents() {
        return Map.of(
            "LIST_SCHEMAS", intents.getLIST_SCHEMAS(),
            "LIST_TABLES", intents.getLIST_TABLES(),
            "DESCRIBE", intents.getDESCRIBE(),
            "QUERY", intents.getQUERY(),
            "SAMPLE", intents.getSAMPLE(),
            "SEARCH", intents.getSEARCH(),
            "REPOSITORIES", intents.getREPOSITORIES(),
            "FILES", intents.getFILES()
        );
    }

    /**
     * Get intent definition by name
     */
    public IntentDefinition getIntent(String intentName) {
        return getAllIntents().get(intentName);
    }
}