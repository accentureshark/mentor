package org.shark.mentor.mcp.infraestructure.config;

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
        private String schemaTypeDatabase = "Database Schema";
        private String schemaStructureAvailable = "Available for querying";
        private String schemaContainsData = "Contains tables and data definitions";
        
        // Universal format templates for different MCP server types
        private String dataQueryFormat = "data title, chart results, summary";
        private String tableSchemaFormat = "name, structure details, size information";
        private String fileFormat = "file name, size, date";
        private String codeGithubFormat = "repository, function/tool, status";
        private String apiToolFormat = "tool name, purpose, results";
        
        // Search keywords (comma-separated)
        private String searchKeywords = "search,find,buscar,encuentra";
    }
}