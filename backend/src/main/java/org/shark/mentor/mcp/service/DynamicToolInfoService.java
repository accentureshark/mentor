package org.shark.mentor.mcp.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.shark.mentor.mcp.model.McpServer;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Service that dynamically analyzes MCP tool information to provide context formatting
 * and eliminates hardcoded data type assumptions
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class DynamicToolInfoService {

    private final McpToolService mcpToolService;

    /**
     * Tool context information extracted from MCP server tools
     */
    public static class ToolContext {
        private final Set<String> domains = new HashSet<>();
        private final Set<String> dataTypes = new HashSet<>();
        private final Set<String> operations = new HashSet<>();
        private final Map<String, String> formatHints = new HashMap<>();

        public Set<String> getDomains() { return domains; }
        public Set<String> getDataTypes() { return dataTypes; }
        public Set<String> getOperations() { return operations; }
        public Map<String, String> getFormatHints() { return formatHints; }
    }

    /**
     * Analyzes available tools from MCP server to determine context type and formatting hints
     */
    public ToolContext analyzeToolContext(McpServer server, String contextContent) {
        List<Map<String, Object>> tools = mcpToolService.getTools(server);
        ToolContext toolContext = new ToolContext();

        // Extract information from tool names and descriptions
        for (Map<String, Object> tool : tools) {
            String name = (String) tool.get("name");
            String description = (String) tool.get("description");

            if (name != null) {
                analyzeName(name, toolContext);
            }
            if (description != null) {
                analyzeDescription(description, toolContext);
            }
        }

        // Determine format hints based on context content and available tools
        determineFormatHints(toolContext, contextContent, tools);

        log.debug("Analyzed tool context for server {}: domains={}, dataTypes={}, operations={}", 
                server.getName(), toolContext.getDomains(), toolContext.getDataTypes(), toolContext.getOperations());

        return toolContext;
    }

    /**
     * Builds dynamic formatting instructions based on tool context
     */
    public String buildDynamicFormatInstructions(ToolContext toolContext, String serverName) {
        StringBuilder instructions = new StringBuilder();
        instructions.append("Organize the information clearly based on the available tools and data types:\n");

        // Database/data-related formatting
        if (toolContext.getDomains().contains("database") || 
            toolContext.getDataTypes().contains("table") ||
            toolContext.getDataTypes().contains("schema")) {
            
            instructions.append("- For database/table information: Use 📁 for names and 🏗️ for structure details\n");
            instructions.append("- List columns with their types and descriptions clearly\n");
            instructions.append("- Include size information and row counts if available\n");
        }

        // Query/data operations formatting
        if (toolContext.getOperations().contains("query") || 
            toolContext.getOperations().contains("search") ||
            toolContext.getOperations().contains("aggregate")) {
            
            instructions.append("- For query results: Use 📊 for data results and 📈 for summaries\n");
            instructions.append("- Highlight key findings and patterns in the data\n");
            instructions.append("- Present data in tabular format when appropriate\n");
        }

        // Repository/code-related formatting
        if (toolContext.getDomains().contains("repository") || 
            toolContext.getDomains().contains("github") ||
            toolContext.getDomains().contains("file")) {
            
            instructions.append("- For code/repository information: Use 💻 for repositories and 🔧 for tools/functions\n");
            instructions.append("- Include repository details, file structures, or code snippets\n");
            instructions.append("- Show status information and execution results\n");
        }

        // Generic formatting for other cases
        if (instructions.length() == 72) { // Only the header was added
            instructions.append("- Use descriptive titles with appropriate emojis\n");
            instructions.append("- Structure information in clear lists\n");
            instructions.append("- Use markdown formatting for readability\n");
        }

        instructions.append("- Use proper spacing between sections\n");
        instructions.append("- If there are multiple results, list them clearly\n");

        return instructions.toString();
    }

    /**
     * Gets the most appropriate emoji prefix based on tool context
     */
    public String getContextEmoji(ToolContext toolContext) {
        if (toolContext.getDataTypes().contains("table") || toolContext.getDataTypes().contains("schema")) {
            return "📁";
        }
        if (toolContext.getOperations().contains("query") || toolContext.getOperations().contains("search")) {
            return "📊";
        }
        if (toolContext.getDomains().contains("repository") || toolContext.getDomains().contains("github")) {
            return "💻";
        }
        return "💡"; // Default
    }

    private void analyzeName(String name, ToolContext toolContext) {
        String lowerName = name.toLowerCase();
        
        // Analyze domains
        if (lowerName.contains("git") || lowerName.contains("repo")) {
            toolContext.getDomains().add("repository");
            toolContext.getDomains().add("github");
        }
        if (lowerName.contains("file") || lowerName.contains("directory")) {
            toolContext.getDomains().add("file");
        }
        if (lowerName.contains("table") || lowerName.contains("schema") || lowerName.contains("query")) {
            toolContext.getDomains().add("database");
        }

        // Analyze data types
        if (lowerName.contains("table")) {
            toolContext.getDataTypes().add("table");
        }
        if (lowerName.contains("schema")) {
            toolContext.getDataTypes().add("schema");
        }
        if (lowerName.contains("column")) {
            toolContext.getDataTypes().add("column");
        }

        // Analyze operations
        if (lowerName.contains("list")) {
            toolContext.getOperations().add("list");
        }
        if (lowerName.contains("search") || lowerName.contains("find")) {
            toolContext.getOperations().add("search");
        }
        if (lowerName.contains("query")) {
            toolContext.getOperations().add("query");
        }
        if (lowerName.contains("describe") || lowerName.contains("get")) {
            toolContext.getOperations().add("describe");
        }
        if (lowerName.contains("count") || lowerName.contains("aggregate") || lowerName.contains("sum") || 
            lowerName.contains("avg") || lowerName.contains("group")) {
            toolContext.getOperations().add("aggregate");
        }
    }

    private void analyzeDescription(String description, ToolContext toolContext) {
        String lowerDesc = description.toLowerCase();
        
        // Extract additional domain information from descriptions
        if (lowerDesc.contains("github") || lowerDesc.contains("repository") || lowerDesc.contains("repo")) {
            toolContext.getDomains().add("repository");
            toolContext.getDomains().add("github");
        }
        if (lowerDesc.contains("database") || lowerDesc.contains("data lake") || lowerDesc.contains("sql")) {
            toolContext.getDomains().add("database");
        }
        if (lowerDesc.contains("file system") || lowerDesc.contains("directory") || lowerDesc.contains("folder")) {
            toolContext.getDomains().add("file");
        }

        // Extract data type information
        if (lowerDesc.contains("table") || lowerDesc.contains("tablas")) {
            toolContext.getDataTypes().add("table");
        }
        if (lowerDesc.contains("schema") || lowerDesc.contains("esquema")) {
            toolContext.getDataTypes().add("schema");
        }
        if (lowerDesc.contains("column") || lowerDesc.contains("columna")) {
            toolContext.getDataTypes().add("column");
        }
    }

    private void determineFormatHints(ToolContext toolContext, String contextContent, List<Map<String, Object>> tools) {
        // Add specific format hints based on the most relevant tools for the context
        String lowerContext = contextContent != null ? contextContent.toLowerCase() : "";
        
        // Determine primary context type
        if (toolContext.getDomains().contains("database")) {
            if (lowerContext.contains("schema") && hasToolContaining(tools, "list_schema")) {
                toolContext.getFormatHints().put("primary", "schema_list");
            } else if (lowerContext.contains("table") && hasToolContaining(tools, "list_table")) {
                toolContext.getFormatHints().put("primary", "table_list");
            } else if (lowerContext.contains("query") || lowerContext.contains("select")) {
                toolContext.getFormatHints().put("primary", "query_result");
            }
        } else if (toolContext.getDomains().contains("repository")) {
            toolContext.getFormatHints().put("primary", "repository_info");
        } else if (toolContext.getDomains().contains("file")) {
            toolContext.getFormatHints().put("primary", "file_info");
        }
    }

    private boolean hasToolContaining(List<Map<String, Object>> tools, String substring) {
        return tools.stream()
                .anyMatch(tool -> {
                    String name = (String) tool.get("name");
                    return name != null && name.toLowerCase().contains(substring);
                });
    }
}