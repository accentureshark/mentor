package org.shark.mentor.mcp.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.shark.mentor.mcp.model.McpServer;
import org.springframework.stereotype.Service;

import java.io.*;
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
        if (root.has("result")) {
            return objectMapper.convertValue(root.get("result"), List.class);
        }
        return Collections.emptyList();
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
            return objectMapper.convertValue(toolsNode, new TypeReference<List<Map<String, Object>>>() {});
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
        prompt.append("You are a tool selector for an MCP (Model Context Protocol) server. ");
        prompt.append("Given the user's message and the available tools, respond with ONLY the exact tool name.\n\n");
        prompt.append("Available tools:\n");
        for (Map<String, Object> tool : tools) {
            prompt.append("- ").append(tool.get("name")).append(": ").append(tool.get("description")).append("\n");
        }
        prompt.append("\nUser message: ").append(userMessage).append("\n");
        prompt.append("Important: Respond with ONLY the tool name, nothing else. No emojis, no formatting, no explanations.\n");
        prompt.append("Tool name: ");
        
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
        
        // Si no se encuentra coincidencia, devolver la primera herramienta como fallback
        log.warn("Could not extract valid tool name from LLM response: '{}'. Available tools: {}. Using first available tool as fallback.", 
                 llmResponse, availableToolNames);
        
        return availableTools.isEmpty() ? null : (String) availableTools.get(0).get("name");
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
        
        Map<String, Object> args = new HashMap<>();
        
        // Solo extraer argumentos para parámetros que realmente existen en el schema
        for (String key : properties.keySet()) {
            Object propertySchema = properties.get(key);
            Object value = llmExtractArgument(userMessage, key, propertySchema, requiredParams.contains(key));
            if (value != null) {
                args.put(key, value);
            }
        }
        
        log.debug("Extracted arguments for tool {}: {}", toolName, args);
        return args;
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
     * Usa LLM para extraer el valor de un argumento específico del mensaje del usuario.
     */
    private Object llmExtractArgument(String userMessage, String key, Object propertySchema, boolean isRequired) {
        // Si el parámetro no es requerido y el mensaje del usuario es muy simple, no extraer
        if (!isRequired && (userMessage == null || userMessage.trim().length() < 3)) {
            return null;
        }
        
        StringBuilder prompt = new StringBuilder();
        prompt.append("Extract the value for the parameter '").append(key).append("' from the user message below.\n");
        prompt.append("Parameter schema: ").append(objectMapper.valueToTree(propertySchema).toPrettyString()).append("\n");
        prompt.append("User message: ").append(userMessage).append("\n");
        
        if (isRequired) {
            prompt.append("This parameter is REQUIRED. ");
        } else {
            prompt.append("This parameter is OPTIONAL. ");
        }
        
        prompt.append("If you cannot extract a meaningful value, respond with 'NULL'.\n");
        prompt.append("Respond with ONLY the parameter value, no explanations:\n");
        prompt.append("Value for '").append(key).append("': ");
        
        String value = llmService.generate(prompt.toString(), "");
        
        if (value == null || value.trim().isEmpty() || "NULL".equalsIgnoreCase(value.trim())) {
            // Para parámetros requeridos, solo usar el mensaje completo como fallback si es muy simple
            if (isRequired && userMessage != null && !userMessage.trim().isEmpty()) {
                // Solo si el mensaje es claramente el valor del parámetro
                if (userMessage.trim().split("\\s+").length <= 5) {
                    log.debug("Using full user message as fallback for required parameter '{}': {}", key, userMessage);
                    return userMessage.trim();
                }
            }
            return null;
        }
        
        // Limpiar la respuesta
        String cleanedValue = value.trim().replaceAll("^[\"'`]+|[\"'`]+$", "");
        return cleanedValue.isEmpty() ? null : cleanedValue;
    }

    /**
     * Llama a una tool vía stdio.
     */
    public String callToolViaStdio(OutputStream stdin, InputStream stdout, String toolName, Map<String, Object> toolArgs) throws Exception {
        Map<String, Object> toolSchema = getToolSchemaByName(toolName);
        if (toolSchema == null) throw new IllegalArgumentException("Tool no encontrada: " + toolName);
        Map<String, Object> call = prepareToolCall(toolSchema, toolArgs);
        return callToolViaStdio(stdin, stdout, call);
    }

    /**
     * Llama a una tool vía stdio usando el objeto toolCall completo.
     */
    public String callToolViaStdio(OutputStream stdin, InputStream stdout, Map<String, Object> toolCall) throws Exception {
        String json = objectMapper.writeValueAsString(toolCall);
        stdin.write((json + "\n").getBytes());
        stdin.flush();
        BufferedReader reader = new BufferedReader(new InputStreamReader(stdout));
        return reader.readLine();
    }

    /**
     * Llama a una tool vía HTTP.
     */
    public String callToolViaHttp(McpServer server, String toolName, Map<String, Object> toolArgs) throws Exception {
        Map<String, Object> toolSchema = getToolSchemaByName(toolName);
        if (toolSchema == null) throw new IllegalArgumentException("Tool no encontrada: " + toolName);
        Map<String, Object> call = prepareToolCall(toolSchema, toolArgs);
        return callToolViaHttp(server, call);
    }

    /**
     * Llama a una tool vía HTTP usando el objeto toolCall completo.
     */
    public String callToolViaHttp(McpServer server, Map<String, Object> toolCall) throws Exception {
        String url = server.getUrl() + "/mcp/tools/call";
        String body = objectMapper.writeValueAsString(toolCall);
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        return response.body();
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
}