package org.shark.mentor.mcp.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;
import java.util.Map;

@Data
@ConfigurationProperties(prefix = "llm")
public class PromptProperties {

    private Map<String, String> prompts;
    private Translation translation = new Translation();

    @Data
    public static class Translation {
        private List<TranslationPattern> patterns;
        private List<TranslationMapping> mappings;
    }

    @Data
    public static class TranslationPattern {
        private String spanish;
        private String english;
    }

    @Data
    public static class TranslationMapping {
        private String spanish;
        private String english;
    }

    public String getToolSelectionPrompt() {
        return prompts.get("tool-selection");
    }

    public String getArgumentExtractionPrompt() {
        return prompts.get("argument-extraction");
    }

    public String buildTranslationPatterns() {
        if (translation.getPatterns() == null || translation.getPatterns().isEmpty()) {
            return "";
        }
        
        StringBuilder sb = new StringBuilder();
        for (TranslationPattern pattern : translation.getPatterns()) {
            sb.append("- \"").append(pattern.getSpanish()).append("\" → ").append(pattern.getEnglish()).append("\n");
        }
        return sb.toString();
    }

    public String buildTranslationMappings() {
        if (translation.getMappings() == null || translation.getMappings().isEmpty()) {
            return "";
        }
        
        StringBuilder sb = new StringBuilder();
        for (TranslationMapping mapping : translation.getMappings()) {
            sb.append("- \"").append(mapping.getSpanish()).append("\" → \"").append(mapping.getEnglish()).append("\"\n");
        }
        return sb.toString();
    }
}