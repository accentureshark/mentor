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
        prompt.append("Dada la siguiente lista de herramientas y el mensaje del usuario, responde únicamente con el nombre exacto de la herramienta más adecuada.\n\n");
        prompt.append("Herramientas disponibles:\n");
        for (Map<String, Object> tool : tools) {
            prompt.append("- ").append(tool.get("name")).append(": ").append(tool.get("description")).append("\n");
        }
        prompt.append("\nMensaje del usuario: ").append(userMessage).append("\n");
        prompt.append("Nombre de la herramienta:");
        String toolName = llmService.generate(prompt.toString(), "");
        // Limpiar posibles saltos de línea o comillas
        return toolName != null ? toolName.trim().replaceAll("[\"']", "") : null;
    }

    /**
     * Extrae los argumentos requeridos para la tool usando LLM y el esquema.
     */
    public Map<String, Object> extractToolArguments(String userMessage, String toolName) {
        Map<String, Object> toolSchema = getToolSchemaByName(toolName);
        if (toolSchema == null) return Collections.emptyMap();
        Map<String, Object> inputSchema = (Map<String, Object>) (toolSchema.get("inputSchema") != null ?
                toolSchema.get("inputSchema") : toolSchema.get("input_schema"));
        Map<String, Object> properties = inputSchema != null ? (Map<String, Object>) inputSchema.get("properties") : Collections.emptyMap();
        Map<String, Object> args = new HashMap<>();
        for (String key : properties.keySet()) {
            Object value = llmExtractArgument(userMessage, key, properties.get(key));
            if (value != null) {
                args.put(key, value);
            }
        }
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
    private Object llmExtractArgument(String userMessage, String key, Object propertySchema) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("Extrae el valor para el campo '").append(key).append("' del siguiente mensaje de usuario, según el esquema:\n");
        prompt.append(objectMapper.valueToTree(propertySchema).toPrettyString()).append("\n");
        prompt.append("Mensaje: ").append(userMessage).append("\n");
        prompt.append("Valor para '").append(key).append("':");
        String value = llmService.generate(prompt.toString(), "");
        if (value == null || value.trim().isEmpty()) {
            // Si no se puede extraer, usar el mensaje completo solo si el campo es requerido
            if (propertySchema instanceof Map && Boolean.TRUE.equals(((Map<?, ?>) propertySchema).get("required"))) {
                return userMessage;
            }
            return null;
        }
        return value.trim().replaceAll("[\"']", "");
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