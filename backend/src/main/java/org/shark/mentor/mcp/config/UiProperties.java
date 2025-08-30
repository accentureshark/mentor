package org.shark.mentor.mcp.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "ui")
public class UiProperties {

    private String locale = "en";
    private Messages messages = new Messages();

    @Data
    public static class Messages {
        private String successPrefix = "✅";
        private String errorPrefix = "❌";
        private String warningPrefix = "⚠️";
        private String infoPrefix = "💡";
        private String dataPrefix = "📊";
        private String toolPrefix = "🔧";
        private String codePrefix = "💻";
        private String filePrefix = "📁";
        private String structurePrefix = "🏗️";
        private String chartPrefix = "📈";
        
        // Template messages
        private String successfulContact = "Successfully contacted %s, but no specific data was returned for: \"%s\"";
        private String responseFrom = "Response from %s";
        private String informationProvidedBy = "Information provided by %s";
        private String dynamicToolsetsEnabled = "Dynamic toolsets enabled for %s";
        private String mcpServerContext = "MCP SERVER CONTEXT";
        private String formattingInstructions = "SPECIFIC FORMATTING INSTRUCTIONS";
        
        // Schema formatting messages
        private String schemasListHeader = "Schemas List";
        private String schemaItemTemplate = "name: %s %s structure: %s Size: %s";
        
        // Search keywords (comma-separated)
        private String searchKeywords = "search,find,buscar,encuentra";
    }
}