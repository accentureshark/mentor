package org.shark.mentor.mcp.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.shark.mentor.mcp.model.McpServer;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class McpToolService {

    private final McpServerService mcpServerService;
    private final LlmService llmService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Obtiene la lista de tools expuestas por el servidor MCP.
     */
    public List<Map<String, Object>> getTools(McpServer server) {
        try {
            String protocol = extractProtocol(server.getUrl());
            if ("stdio".equalsIgnoreCase(protocol)) {
                return getToolsViaStdio(server);
            } else {
                return getToolsViaHttp(server);
            }
        } catch (Exception e) {
            log.error("Error obteniendo tools para {}: {}", server.getName(), e.getMessage());
            return Collections.emptyList();
        }
    }

    private List<Map<String, Object>> getToolsViaStdio(McpServer server) throws Exception {
        OutputStream stdin = mcpServerService.getStdioInput(server.getId());
        InputStream stdout = mcpServerService.getStdioOutput(server.getId());
        if (stdin == null || stdout == null) {
            throw new IllegalStateException("STDIO streams no disponibles para el servidor: " + server.getId());
        }
        Map<String, Object> request = Map.of(
                "jsonrpc", "2.0",
                "id", UUID.randomUUID().toString(),
                "method", "tools/list",
                "params", Collections.emptyMap()
        );
        String json = objectMapper.writeValueAsString(request);
        stdin.write((json + "\n").getBytes());
        stdin.flush();

        BufferedReader reader = new BufferedReader(new InputStreamReader(stdout));
        String line = reader.readLine();
        JsonNode root = objectMapper.readTree(line);
        JsonNode toolsNode = null;
        if (root.isArray()) {
            toolsNode = root;
        } else if (root.has("result")) {
            JsonNode result = root.get("result");
            if (result.isArray()) {
                toolsNode = result;
            } else if (result.has("tools")) {
                toolsNode = result.get("tools");
            }
        } else if (root.has("tools")) {
            toolsNode = root.get("tools");
        }
        if (toolsNode != null && toolsNode.isArray()) {
            return objectMapper.convertValue(toolsNode, new com.fasterxml.jackson.core.type.TypeReference<List<Map<String, Object>>>() {});
        } else {
            log.warn("No se encontró un array de tools en la respuesta STDIO: {}", line);
            return Collections.emptyList();
        }
    }

    public List<Map<String, Object>> getToolsViaHttp(McpServer server) throws Exception {
        String url = server.getUrl() + "/mcp/tools/list";
        String body = "{\"jsonrpc\":\"2.0\",\"id\":\"" + UUID.randomUUID() + "\",\"method\":\"tools/list\",\"params\":{}}";
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        HttpResponse<String> response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
        JsonNode root = objectMapper.readTree(response.body());

        // Soporta ambos formatos: array directo o dentro de un campo
        JsonNode toolsNode = null;
        if (root.isArray()) {
            toolsNode = root;
        } else if (root.has("result")) {
            JsonNode result = root.get("result");
            if (result.isArray()) {
                toolsNode = result;
            } else if (result.has("tools")) {
                toolsNode = result.get("tools");
            }
        } else if (root.has("tools")) {
            toolsNode = root.get("tools");
        }

        if (toolsNode != null && toolsNode.isArray()) {
            return objectMapper.convertValue(toolsNode, new TypeReference<List<Map<String, Object>>>() {
            });
        } else {
            log.warn("No se encontró un array de tools en la respuesta: {}", response.body());
            return Collections.emptyList();
        }
    }

    /**
     * Selecciona la mejor tool para el mensaje del usuario usando LLM.
     */
    public String selectBestTool(String userMessage, McpServer server) {
        List<Map<String, Object>> tools = getTools(server);

        if (tools.isEmpty()) return null;

        StringBuilder prompt = new StringBuilder();

        prompt.append("Eres un selector de herramientas para un servidor MCP (Model Context Protocol). ");
        prompt.append("Dado el mensaje del usuario y las herramientas disponibles, responde ÚNICAMENTE con el nombre exacto de la herramienta.\n\n");
        prompt.append("Herramientas disponibles:\n");
        for (Map<String, Object> tool : tools) {
            prompt.append("- ").append(tool.get("name")).append(": ").append(tool.get("description")).append("\n");
        }
        prompt.append("\nMensaje del usuario: ").append(userMessage).append("\n");
        prompt.append("Importante: Responde ÚNICAMENTE con el nombre de la herramienta, nada más. Sin emojis, sin formato, sin explicaciones.\n");
        prompt.append("Nombre de la herramienta: ");

        String toolName = llmService.generate(prompt.toString(), "");
        return extractToolNameFromLlmResponse(toolName, tools);
    }

    /**
     * Extrae el nombre de herramienta real de la respuesta del LLM, manejando respuestas formateadas.
     */
    private String extractToolNameFromLlmResponse(String llmResponse, List<Map<String, Object>> availableTools) {
        if (llmResponse == null || llmResponse.trim().isEmpty()) {
            return null;
        }

        // Limpiar respuesta básica
        String cleaned = llmResponse.trim().replaceAll("[\"'`]", "");

        // Crear un set de nombres de herramientas disponibles para matching rápido
        Set<String> availableToolNames = new HashSet<>();
        for (Map<String, Object> tool : availableTools) {
            String toolName = (String) tool.get("name");
            if (toolName != null) {
                availableToolNames.add(toolName);
            }
        }

        // Caso 1: La respuesta ya es un nombre de herramienta válido
        if (availableToolNames.contains(cleaned)) {
            return cleaned;
        }

        // Caso 2: La respuesta contiene formatting (ej: "📁 **list_tables**")
        // Buscar coincidencias de nombres de herramientas en la respuesta
        for (String toolName : availableToolNames) {
            if (cleaned.contains(toolName)) {
                return toolName;
            }
        }

        // Caso 3: Intentar extraer usando regex para patrones comunes
        // Patrón para **nombre_herramienta**
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("\\*\\*([a-zA-Z_][a-zA-Z0-9_]*)\\*\\*");
        java.util.regex.Matcher matcher = pattern.matcher(cleaned);
        if (matcher.find()) {
            String extracted = matcher.group(1);
            if (availableToolNames.contains(extracted)) {
                return extracted;
            }
        }

        // Caso 4: Buscar palabras individuales que coincidan con nombres de herramientas
        String[] words = cleaned.split("\\s+");
        for (String word : words) {
            String cleanWord = word.replaceAll("[^a-zA-Z0-9_]", "");
            if (availableToolNames.contains(cleanWord)) {
                return cleanWord;
            }
        }

        // Si no se encuentra coincidencia, devolver null en lugar de usar la primera herramienta
        log.warn("Could not extract valid tool name from LLM response: '{}'. Available tools: {}",
                llmResponse, availableToolNames);

        return null;
    }

    /**
     * Extrae los argumentos requeridos para la tool usando LLM y el esquema.
     */
    public Map<String, Object> extractToolArguments(String userMessage, String toolName) {
        Map<String, Object> toolSchema = getToolSchemaByName(toolName);
        if (toolSchema == null) {
            log.warn("Tool schema not found for tool: {}", toolName);
            return Collections.emptyMap();
        }

        Map<String, Object> inputSchema = (Map<String, Object>) (toolSchema.get("inputSchema") != null ?
                toolSchema.get("inputSchema") : toolSchema.get("input_schema"));

        if (inputSchema == null) {
            log.debug("No input schema found for tool: {}", toolName);
            return Collections.emptyMap();
        }

        Map<String, Object> properties = (Map<String, Object>) inputSchema.get("properties");
        if (properties == null || properties.isEmpty()) {
            log.debug("Tool {} has no parameters", toolName);
            return Collections.emptyMap();
        }

        // Obtener lista de parámetros requeridos
        List<String> requiredParams = (List<String>) inputSchema.get("required");
        if (requiredParams == null) {
            requiredParams = Collections.emptyList();
        }

        // Construir un único prompt para extraer todos los argumentos
        StringBuilder prompt = new StringBuilder();
        prompt.append("Extract the values for the following parameters from the user message.\\n");
        prompt.append("Return a JSON object with the parameters as keys. Use null when a value cannot be determined.\\n");
        prompt.append("User message: ").append(userMessage).append("\\n");
        prompt.append("Parameters schema: ").append(objectMapper.valueToTree(properties).toString()).append("\\n");
        prompt.append("JSON response: ");

        String llmResponse = llmService.generate(prompt.toString(), "{}");

        Map<String, Object> extracted;
        try {
            extracted = objectMapper.readValue(llmResponse, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            log.warn("Failed to parse LLM arguments for tool {}: {}", toolName, e.getMessage());
            return Collections.emptyMap();
        }

        // Fallback para parámetros requeridos si el LLM no los proporciona
        for (String req : requiredParams) {
            Object val = extracted.get(req);
            if (val == null || (val instanceof String && ((String) val).trim().isEmpty())) {
                if (userMessage != null && userMessage.trim().split("\\s+").length <= 5) {
                    extracted.put(req, userMessage.trim());
                }
            }
        }

        try {
            Map<String, Object> validated = validateArguments(toolSchema, extracted);
            log.debug("Extracted arguments for tool {}: {}", toolName, validated);
            return validated;
        } catch (IllegalArgumentException e) {
            log.warn("Argument validation failed for tool {}: {}", toolName, e.getMessage());
            return Collections.emptyMap();
        }
    }

    /**
     * Busca el esquema de la tool por nombre en todos los servidores conectados.
     */
    public Map<String, Object> getToolSchemaByName(String toolName) {
        for (McpServer server : mcpServerService.getAllServers()) {
            List<Map<String, Object>> tools = getTools(server);
            for (Map<String, Object> tool : tools) {
                if (toolName.equals(tool.get("name"))) {
                    return tool;
                }
            }
        }
        return null;
    }

    /**
     * Llama a una tool determinando automáticamente el protocolo.
     */
    public String callTool(McpServer server, String toolName, Map<String, Object> toolArgs) throws Exception {
        Map<String, Object> toolSchema = getToolSchemaByName(toolName);
        if (toolSchema == null) {
            throw new IllegalArgumentException("Tool no encontrada: " + toolName);
        }
        Map<String, Object> validatedArgs = validateArguments(toolSchema, toolArgs);
        Map<String, Object> call = prepareToolCall(toolSchema, validatedArgs);
        return callTool(server, call);
    }

    /**
     * Envía un toolCall al servidor usando HTTP o STDIO según corresponda.
     */
    public String callTool(McpServer server, Map<String, Object> toolCall) throws Exception {
        String protocol = extractProtocol(server.getUrl());
        if ("stdio".equalsIgnoreCase(protocol)) {
            OutputStream stdin = mcpServerService.getStdioInput(server.getId());
            InputStream stdout = mcpServerService.getStdioOutput(server.getId());
            if (stdin == null || stdout == null) {
                throw new IllegalStateException("STDIO streams no disponibles para el servidor: " + server.getId());
            }
            return sendToolCallViaStdio(stdin, stdout, toolCall);
        } else {
            return sendToolCallViaHttp(server, toolCall);
        }
    }

    private String sendToolCallViaStdio(OutputStream stdin, InputStream stdout, Map<String, Object> toolCall) throws Exception {
        String json = objectMapper.writeValueAsString(toolCall);
        stdin.write((json + "\n").getBytes(StandardCharsets.UTF_8));
        stdin.flush();
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        int ch;
        while ((ch = stdout.read()) != -1) {
            if (ch == '\n') break;
            buffer.write(ch);
        }
        return buffer.toString(StandardCharsets.UTF_8);
    }

    private String sendToolCallViaHttp(McpServer server, Map<String, Object> toolCall) throws Exception {
        String url = server.getUrl() + "/mcp/tools/call";
        String body = objectMapper.writeValueAsString(toolCall);
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
        try (InputStream is = response.body()) {
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    /**
     * Valida y normaliza los argumentos según el schema de la tool.
     */
    private Map<String, Object> validateArguments(Map<String, Object> toolSchema, Map<String, Object> arguments) {
        Map<String, Object> inputSchema = (Map<String, Object>) (toolSchema.get("inputSchema") != null ?
                toolSchema.get("inputSchema") : toolSchema.get("input_schema"));
        Map<String, Object> properties = inputSchema != null ? (Map<String, Object>) inputSchema.get("properties") : Collections.emptyMap();
        List<String> required = inputSchema != null && inputSchema.get("required") instanceof List ?
                (List<String>) inputSchema.get("required") : Collections.emptyList();

        Map<String, Object> normalized = new HashMap<>();

        // Validar requeridos
        for (String req : required) {
            Object val = arguments.get(req);
            if (val == null || (val instanceof String && ((String) val).trim().isEmpty())) {
                throw new IllegalArgumentException("Missing required parameter: " + req);
            }
        }

        for (Map.Entry<String, Object> entry : arguments.entrySet()) {
            String key = entry.getKey();
            Object schema = properties.get(key);
            if (!(schema instanceof Map)) continue;
            String type = (String) ((Map<String, Object>) schema).get("type");
            Object value = entry.getValue();
            if (value == null) {
                normalized.put(key, null);
                continue;
            }
            try {
                if ("integer".equals(type)) {
                    if (value instanceof Number) {
                        normalized.put(key, ((Number) value).intValue());
                    } else {
                        normalized.put(key, Integer.parseInt(value.toString()));
                    }
                } else if ("number".equals(type)) {
                    if (value instanceof Number) {
                        normalized.put(key, ((Number) value).doubleValue());
                    } else {
                        normalized.put(key, Double.parseDouble(value.toString()));
                    }
                } else if ("boolean".equals(type)) {
                    if (value instanceof Boolean) {
                        normalized.put(key, value);
                    } else {
                        normalized.put(key, Boolean.parseBoolean(value.toString()));
                    }
                } else { // default string
                    normalized.put(key, value.toString());
                }
            } catch (Exception e) {
                throw new IllegalArgumentException("Invalid value for parameter '" + key + "': " + value);
            }
        }

        return normalized;
    }

    /**
     * Prepara el objeto toolCall para la llamada a la tool.
     */
    public Map<String, Object> prepareToolCall(Map<String, Object> toolSchema, Map<String, Object> arguments) {
        Map<String, Object> inputSchema = (Map<String, Object>) (toolSchema.get("inputSchema") != null ?
                toolSchema.get("inputSchema") : toolSchema.get("input_schema"));
        Map<String, Object> properties = inputSchema != null ? (Map<String, Object>) inputSchema.get("properties") : Collections.emptyMap();
        Map<String, Object> filteredArgs = new HashMap<>();
        for (String key : properties.keySet()) {
            if (arguments.containsKey(key)) {
                filteredArgs.put(key, arguments.get(key));
            }
        }
        Map<String, Object> params = new HashMap<>();
        params.put("name", toolSchema.get("name"));
        params.put("arguments", filteredArgs);
        Map<String, Object> call = new HashMap<>();
        call.put("jsonrpc", "2.0");
        call.put("id", UUID.randomUUID().toString());
        call.put("method", "tools/call");
        call.put("params", params);
        return call;
    }

    /**
     * Devuelve todas las tools de todos los servidores.
     */
    public List<Map<String, Object>> getAllTools() {
        List<Map<String, Object>> all = new ArrayList<>();
        for (McpServer server : mcpServerService.getAllServers()) {
            all.addAll(getTools(server));
        }
        return all;
    }

    private String extractProtocol(String url) {
        if (url == null || !url.contains("://")) {
            return "unknown";
        }
        return url.substring(0, url.indexOf("://"));
    }

    /**
     * Infers the best tool and its arguments for a user message, using only the available tools' metadata.
     * This method is generic and extensible: it does not assume any domain (SQL, REST, etc).
     * It uses the LLM to select the tool and extract arguments based on the tool's schema.
     *
     * @param userMessage the user's message
     * @param availableTools the list of tools (with schema/description)
     * @return Map with keys: "tool" (String) and "arguments" (Map<String, Object>)
     */
    public Map<String, Object> inferToolAndArguments(String userMessage, List<Map<String, Object>> availableTools) {
        if (availableTools == null || availableTools.isEmpty()) return null;

        // 1. Seleccionar la mejor tool usando el LLM y la metadata
        StringBuilder toolPrompt = new StringBuilder();
        toolPrompt.append("Eres un selector de herramientas para un cliente MCP. ");
        toolPrompt.append("Dado el mensaje del usuario y la lista de herramientas, responde ÚNICAMENTE con el nombre exacto de la herramienta más adecuada.\n\n");
        toolPrompt.append("Herramientas disponibles:\n");
        for (Map<String, Object> tool : availableTools) {
            toolPrompt.append("- ").append(tool.get("name")).append(": ").append(tool.get("description")).append("\n");
        }
        toolPrompt.append("\nMensaje del usuario: ").append(userMessage).append("\n");
        toolPrompt.append("Importante: Responde ÚNICAMENTE con el nombre de la herramienta, nada más.\n");
        toolPrompt.append("Nombre de la herramienta: ");
        String toolName = llmService.generate(toolPrompt.toString(), "");
        toolName = extractToolNameFromLlmResponse(toolName, availableTools);
        if (toolName == null) return null;

        // 2. Extraer argumentos usando el LLM y el schema de la tool
        Map<String, Object> arguments = extractToolArguments(userMessage, toolName);

        Map<String, Object> result = new HashMap<>();
        result.put("tool", toolName);
        result.put("arguments", arguments);
        return result;
    }
}