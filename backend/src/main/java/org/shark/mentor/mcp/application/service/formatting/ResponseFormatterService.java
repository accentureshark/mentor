package org.shark.mentor.mcp.application.service.formatting;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.shark.mentor.mcp.application.service.i18n.I18nService;
import org.shark.mentor.mcp.domain.model.McpServer;
import org.springframework.stereotype.Service;

import java.util.regex.Pattern;

/**
 * Service for formatting MCP responses without requiring LLM calls for common patterns.
 * This provides fast template-based formatting for structured data while preserving
 * natural language quality for complex responses.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class ResponseFormatterService {

    private final I18nService i18nService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    // Pattern matchers for common response types
    private static final Pattern JSON_PATTERN = Pattern.compile("^\\s*[\\[{].*[}\\]]\\s*$", Pattern.DOTALL);
    private static final Pattern SCHEMA_PATTERN = Pattern.compile("(?i).*(schema|information_schema|table).*", Pattern.DOTALL);
    private static final Pattern FILE_PATTERN = Pattern.compile("(?i).*(file|directory|folder|path).*", Pattern.DOTALL);
    private static final Pattern LIST_PATTERN = Pattern.compile(".*[\\*\\-]\\s+.*", Pattern.DOTALL);
    private static final Pattern ERROR_PATTERN = Pattern.compile("(?i).*(error|exception|failed|invalid).*", Pattern.DOTALL);

    /**
     * Attempts to format the MCP response using templates without LLM.
     * Returns null if the response is too complex and requires LLM processing.
     */
    public String tryFormatWithoutLlm(String mcpContext, String userMessage, McpServer server) {
        if (mcpContext == null || mcpContext.trim().isEmpty()) {
            return formatEmptyResponse(server);
        }

        try {
            // Check if response is JSON structured data
            if (JSON_PATTERN.matcher(mcpContext.trim()).matches()) {
                return formatJsonResponse(mcpContext, userMessage, server);
            }

            // Check for common text patterns
            if (SCHEMA_PATTERN.matcher(mcpContext).matches() && 
                (userMessage.toLowerCase().contains("schema") || userMessage.toLowerCase().contains("esquema"))) {
                return formatSchemaListResponse(mcpContext, server);
            }

            if (FILE_PATTERN.matcher(mcpContext).matches() && 
                (userMessage.toLowerCase().contains("file") || userMessage.toLowerCase().contains("archivo"))) {
                return formatFileListResponse(mcpContext, server);
            }

            if (LIST_PATTERN.matcher(mcpContext).matches()) {
                return formatListResponse(mcpContext, server);
            }

            if (ERROR_PATTERN.matcher(mcpContext).matches()) {
                return formatErrorResponse(mcpContext, server);
            }

            // For simple text responses (under 200 characters, no complex structure)
            if (mcpContext.length() < 200 && !mcpContext.contains("\n\n") && 
                mcpContext.split("\n").length <= 3) {
                return formatSimpleTextResponse(mcpContext, server);
            }

        } catch (Exception e) {
            log.debug("Error in template-based formatting, will fall back to LLM: {}", e.getMessage());
        }

        // Return null to indicate LLM processing is needed
        return null;
    }

    private String formatJsonResponse(String mcpContext, String userMessage, McpServer server) {
        try {
            JsonNode jsonNode = objectMapper.readTree(mcpContext);
            
            if (isSchemaResponse(jsonNode, userMessage)) {
                return formatJsonSchemaResponse(jsonNode, server);
            }
            
            if (isFileResponse(jsonNode, userMessage)) {
                return formatJsonFileResponse(jsonNode, server);
            }
            
            return formatGenericJsonResponse(jsonNode, server);
            
        } catch (Exception e) {
            return null; // Fall back to LLM
        }
    }

    private boolean isSchemaResponse(JsonNode jsonNode, String userMessage) {
        String jsonString = jsonNode.toString().toLowerCase();
        String userQuery = userMessage != null ? userMessage.toLowerCase() : "";
        
        return (userQuery.contains("schema") || userQuery.contains("esquema") || 
                userQuery.contains("tabla") || userQuery.contains("table")) &&
               (jsonString.contains("schema") || jsonString.contains("table"));
    }

    private boolean isFileResponse(JsonNode jsonNode, String userMessage) {
        String jsonString = jsonNode.toString().toLowerCase();
        String userQuery = userMessage != null ? userMessage.toLowerCase() : "";
        
        return (userQuery.contains("file") || userQuery.contains("archivo") || 
                userQuery.contains("directory") || userQuery.contains("folder")) &&
               (jsonString.contains("file") || jsonString.contains("path") || 
                jsonString.contains("directory"));
    }

    private String formatJsonSchemaResponse(JsonNode jsonNode, McpServer server) {
        StringBuilder response = new StringBuilder();
        response.append(formatHeader(server, "🏗️ Schemas Disponibles"));
        
        if (jsonNode.has("schemas") && jsonNode.get("schemas").isArray()) {
            for (JsonNode schema : jsonNode.get("schemas")) {
                String schemaName = schema.has("name") ? schema.get("name").asText() : "unknown";
                response.append(formatSchemaItem(schemaName));
            }
        } else if (jsonNode.isArray()) {
            for (JsonNode schema : jsonNode) {
                String schemaName = schema.isTextual() ? schema.asText() : 
                                   schema.has("name") ? schema.get("name").asText() : "unknown";
                response.append(formatSchemaItem(schemaName));
            }
        } else {
            // Simple JSON display
            try {
                String prettyJson = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(jsonNode);
                response.append("```json\n").append(prettyJson).append("\n```\n");
            } catch (Exception e) {
                response.append(jsonNode.toString());
            }
        }
        
        response.append(formatFooter(server));
        return response.toString();
    }

    private String formatJsonFileResponse(JsonNode jsonNode, McpServer server) {
        StringBuilder response = new StringBuilder();
        response.append(formatHeader(server, "📁 Archivos Encontrados"));
        
        JsonNode files = jsonNode.has("files") ? jsonNode.get("files") : 
                        jsonNode.has("result") ? jsonNode.get("result").get("files") : jsonNode;
        
        if (files.isArray()) {
            for (JsonNode file : files) {
                response.append(formatFileItem(file));
            }
        } else if (files.isObject()) {
            response.append(formatFileItem(files));
        }
        
        response.append(formatFooter(server));
        return response.toString();
    }

    private String formatGenericJsonResponse(JsonNode jsonNode, McpServer server) {
        StringBuilder response = new StringBuilder();
        response.append(formatHeader(server, "📊 Información Estructurada"));
        
        try {
            String prettyJson = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(jsonNode);
            response.append("```json\n").append(prettyJson).append("\n```\n");
        } catch (Exception e) {
            response.append(jsonNode.toString());
        }
        
        response.append(formatFooter(server));
        return response.toString();
    }

    private String formatSchemaListResponse(String mcpContext, McpServer server) {
        StringBuilder response = new StringBuilder();
        response.append(formatHeader(server, "🏗️ Schemas Disponibles"));
        
        String[] lines = mcpContext.split("\n");
        for (String line : lines) {
            line = line.trim();
            if (!line.isEmpty() && !line.toLowerCase().contains("available") && 
                !line.toLowerCase().contains("schema") && line.matches("^[a-zA-Z0-9_]+$")) {
                response.append(formatSchemaItem(line));
            }
        }
        
        response.append(formatFooter(server));
        return response.toString();
    }

    private String formatFileListResponse(String mcpContext, McpServer server) {
        StringBuilder response = new StringBuilder();
        response.append(formatHeader(server, "📁 Lista de Archivos"));
        
        String[] lines = mcpContext.split("\n");
        for (String line : lines) {
            line = line.trim();
            if (!line.isEmpty() && (line.contains("/") || line.contains(".") || line.matches(".*\\.(txt|json|xml|csv|log).*"))) {
                response.append("📄 **").append(line).append("**\n");
            } else if (!line.isEmpty()) {
                response.append("🔹 ").append(line).append("\n");
            }
        }
        
        response.append(formatFooter(server));
        return response.toString();
    }

    private String formatListResponse(String mcpContext, McpServer server) {
        StringBuilder response = new StringBuilder();
        response.append(formatHeader(server, "📋 Lista de Elementos"));
        
        String[] lines = mcpContext.split("\n");
        for (String line : lines) {
            line = line.trim();
            if (line.startsWith("* ") || line.startsWith("- ")) {
                response.append("🔹 ").append(line.substring(2)).append("\n");
            } else if (!line.isEmpty()) {
                response.append(line).append("\n");
            }
        }
        
        response.append(formatFooter(server));
        return response.toString();
    }

    private String formatErrorResponse(String mcpContext, McpServer server) {
        StringBuilder response = new StringBuilder();
        response.append("❌ **Error en ").append(server.getName()).append("**\n\n");
        response.append("📝 ").append(mcpContext);
        response.append("\n\n💡 *Error reportado por ").append(server.getName()).append("*");
        return response.toString();
    }

    private String formatSimpleTextResponse(String mcpContext, McpServer server) {
        StringBuilder response = new StringBuilder();
        response.append(formatHeader(server, "📝 Respuesta"));
        response.append(mcpContext);
        response.append("\n\n");
        response.append(formatFooter(server));
        return response.toString();
    }

    private String formatEmptyResponse(McpServer server) {
        return "⚠️ **Sin Resultados**\n\nNo se encontraron resultados para la consulta.\n\n💡 *Consultado en " + server.getName() + "*";
    }

    private String formatHeader(McpServer server, String title) {
        return "✅ **Respuesta de " + server.getName() + "**\n\n" + title + "\n\n";
    }

    private String formatFooter(McpServer server) {
        return "\n💡 *Información proporcionada por " + server.getName() + "*";
    }

    private String formatSchemaItem(String schemaName) {
        return String.format("📁 **%s**\n🏗️ Estructura: Esquema de base de datos\n📏 Tamaño: No especificado\n\n", schemaName);
    }

    private String formatFileItem(JsonNode file) {
        StringBuilder item = new StringBuilder();
        String fileName = file.has("name") ? file.get("name").asText() : 
                         file.has("path") ? file.get("path").asText() : "archivo";
        
        item.append("📄 **").append(fileName).append("**\n");
        
        if (file.has("size")) {
            item.append("📏 Tamaño: ").append(file.get("size").asText()).append("\n");
        }
        if (file.has("modified") || file.has("lastModified")) {
            String modified = file.has("modified") ? file.get("modified").asText() :
                             file.get("lastModified").asText();
            item.append("📅 Modificado: ").append(modified).append("\n");
        }
        if (file.has("type")) {
            item.append("🏷️ Tipo: ").append(file.get("type").asText()).append("\n");
        }
        
        item.append("\n");
        return item.toString();
    }
}