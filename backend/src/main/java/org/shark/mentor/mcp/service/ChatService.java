package org.shark.mentor.mcp.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.shark.mentor.mcp.model.ChatMessage;
import org.shark.mentor.mcp.model.McpServer;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
public class ChatService {

    private final Map<String, List<ChatMessage>> conversations = new ConcurrentHashMap<>();
    private final McpServerService mcpServerService;
    private final LlmService llmService;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public ChatService(McpServerService mcpServerService, LlmService llmService) {
        this.mcpServerService = mcpServerService;
        this.llmService = llmService;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    private List<Map<String, Object>> getToolsViaHttp(McpServer server) {
        try {
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("jsonrpc", "2.0");
            requestBody.put("id", UUID.randomUUID().toString());
            requestBody.put("method", "tools/list");
            requestBody.put("params", new HashMap<>());

            String jsonBody = convertToJson(requestBody);
            log.debug("Requesting tools from {}: {}", server.getUrl(), jsonBody);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(server.getUrl() + "/mcp"))
                    .timeout(Duration.ofSeconds(10))
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            String responseText = response.body();
            log.debug("tools/list raw response from {}: {}", server.getName(), responseText);

            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                JsonNode rootNode = objectMapper.readTree(responseText);

                if (!rootNode.isObject()) {
                    log.warn("Expected JSON object from tools/list, but got: {}", rootNode);
                    return getDefaultToolsForServer(server);
                }

                Map<String, Object> responseJson = objectMapper.convertValue(rootNode, Map.class);

                if (responseJson.containsKey("result") && responseJson.get("result") instanceof Map) {
                    Map<String, Object> result = (Map<String, Object>) responseJson.get("result");
                    if (result.containsKey("tools") && result.get("tools") instanceof List) {
                        return (List<Map<String, Object>>) result.get("tools");
                    }
                }
            } else {
                log.warn("HTTP error from tools/list on {}: status={}, body={}",
                        server.getName(), response.statusCode(), responseText);
            }
        } catch (Exception e) {
            log.warn("Failed to get tools via HTTP from {}: {}", server.getName(), e.getMessage());
        }

        return getDefaultToolsForServer(server);
    }


    private List<Map<String, Object>> getToolsViaHttp(McpServer server) {
        try {
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("jsonrpc", "2.0");
            requestBody.put("id", UUID.randomUUID().toString());
            requestBody.put("method", "tools/list");
            requestBody.put("params", new HashMap<>());

            String jsonBody = convertToJson(requestBody);
            log.debug("Requesting tools from {}: {}", server.getUrl(), jsonBody);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(server.getUrl() + "/mcp"))
                    .timeout(Duration.ofSeconds(10))
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                Map<String, Object> responseJson = objectMapper.readValue(response.body(), Map.class);
                if (responseJson.containsKey("result") && responseJson.get("result") instanceof Map) {
                    Map<String, Object> result = (Map<String, Object>) responseJson.get("result");
                    if (result.containsKey("tools") && result.get("tools") instanceof List) {
                        return (List<Map<String, Object>>) result.get("tools");
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Failed to get tools via HTTP from {}: {}", server.getName(), e.getMessage());
        }

        return getDefaultToolsForServer(server);
    }

    private List<Map<String, Object>> getDefaultToolsForServer(McpServer server) {
        List<Map<String, Object>> tools = new ArrayList<>();

        // Configurar herramientas por defecto basadas en el tipo de servidor
        if ("melian-local".equals(server.getId())) {
            // Herramientas para el servidor local MELIAN
            Map<String, Object> searchTool = new HashMap<>();
            searchTool.put("name", "search_characters");
            searchTool.put("description", "Search for character information");
            Map<String, Object> inputSchema = new HashMap<>();
            inputSchema.put("type", "object");
            Map<String, Object> properties = new HashMap<>();
            Map<String, Object> queryProp = new HashMap<>();
            queryProp.put("type", "string");
            queryProp.put("description", "Character name to search for");
            properties.put("query", queryProp);
            inputSchema.put("properties", properties);
            inputSchema.put("required", Arrays.asList("query"));
            searchTool.put("inputSchema", inputSchema);
            tools.add(searchTool);

            // Agregar herramienta de búsqueda general
            Map<String, Object> generalSearch = new HashMap<>();
            generalSearch.put("name", "search");
            generalSearch.put("description", "General search functionality");
            generalSearch.put("inputSchema", inputSchema);
            tools.add(generalSearch);
        } else {
            // Herramientas genéricas para otros servidores
            Map<String, Object> defaultTool = new HashMap<>();
            defaultTool.put("name", "search");
            defaultTool.put("description", "Default search tool");
            Map<String, Object> inputSchema = new HashMap<>();
            inputSchema.put("type", "object");
            Map<String, Object> properties = new HashMap<>();
            Map<String, Object> queryProp = new HashMap<>();
            queryProp.put("type", "string");
            queryProp.put("description", "Search query");
            properties.put("query", queryProp);
            inputSchema.put("properties", properties);
            inputSchema.put("required", Arrays.asList("query"));
            defaultTool.put("inputSchema", inputSchema);
            tools.add(defaultTool);
        }

        return tools;
    }

    private String extractQueryFromMessage(String message) {
        // Extraer la consulta específica del mensaje del usuario
        String lowerMessage = message.toLowerCase().trim();

        // Para consultas sobre personajes específicos
        if (lowerMessage.contains("iron man")) {
            return "Iron Man";
        }
        if (lowerMessage.contains("spider-man") || lowerMessage.contains("spiderman")) {
            return "Spider-Man";
        }
        if (lowerMessage.contains("batman")) {
            return "Batman";
        }
        if (lowerMessage.contains("superman")) {
            return "Superman";
        }

        // Para consultas más generales, extraer palabras clave
        String[] words = message.split("\\s+");
        for (String word : words) {
            // Buscar palabras que parezcan nombres propios (empiezan con mayúscula)
            if (word.length() > 2 && Character.isUpperCase(word.charAt(0))) {
                return word;
            }
        }

        // Si no se encuentra nada específico, devolver el mensaje completo
        return message.trim();
    }

    private Map<String, Object> selectBestTool(String userMessage, List<Map<String, Object>> availableTools) {
        log.debug("Selecting best tool for message: '{}' from {} available tools", userMessage, availableTools.size());

        if (availableTools.isEmpty()) {
            return null;
        }

        String lowerMessage = userMessage.toLowerCase();

        // Mapeo inteligente basado en palabras clave en el mensaje del usuario
        for (Map<String, Object> tool : availableTools) {
            String toolName = (String) tool.get("name");

            if (toolName == null) continue;

            // Priorizar search_movies para consultas sobre personajes de películas/superhéroes
            if ((lowerMessage.contains("iron man") || lowerMessage.contains("spider-man") ||
                    lowerMessage.contains("batman") || lowerMessage.contains("superman") ||
                    lowerMessage.contains("personaje") || lowerMessage.contains("character") ||
                    lowerMessage.contains("héroe") || lowerMessage.contains("hero") ||
                    lowerMessage.contains("superhéroe") || lowerMessage.contains("datos") ||
                    lowerMessage.contains("información")) &&
                    toolName.equals("search_movies")) {
                log.info("Selected tool '{}' for character/movie search", toolName);
                return tool;
            }

            // Búsqueda de películas específicas
            if ((lowerMessage.contains("película") || lowerMessage.contains("movie") ||
                    lowerMessage.contains("film")) &&
                    (toolName.contains("movie") || toolName.contains("search_movies"))) {
                log.info("Selected tool '{}' for movie search", toolName);
                return tool;
            }

            // Búsqueda de personajes/caracteres con herramientas específicas
            if ((lowerMessage.contains("personaje") || lowerMessage.contains("character") ||
                    lowerMessage.contains("héroe") || lowerMessage.contains("hero") ||
                    lowerMessage.contains("superhéroe")) &&
                    (toolName.contains("character") || toolName.contains("search_characters"))) {
                log.info("Selected tool '{}' for character search", toolName);
                return tool;
            }

            // Búsqueda general
            if ((lowerMessage.contains("buscar") || lowerMessage.contains("search") ||
                    lowerMessage.contains("encontrar") || lowerMessage.contains("datos") ||
                    lowerMessage.contains("información")) &&
                    (toolName.equals("search") || toolName.contains("search"))) {
                log.info("Selected tool '{}' for general search", toolName);
                return tool;
            }
        }

        // Si no hay coincidencia específica, usar la primera herramienta disponible
        Map<String, Object> defaultTool = availableTools.get(0);
        log.info("No specific tool match found, using default tool: {}", defaultTool.get("name"));
        return defaultTool;
    }

    private String extractProtocol(String url) {
        if (url == null || !url.contains("://")) {
            return "unknown";
        }
        return url.substring(0, url.indexOf("://"));
    }

    private String handleStdioMessage(McpServer server, String message, Map<String, Object> selectedTool) {
        log.info("Handling stdio message for server: {}", server.getName());

        try {
            // Extraer el comando del URL stdio://
            String command = server.getUrl().substring("stdio://".length());
            String[] commandParts = command.split("\\s+");

            // Crear el proceso
            ProcessBuilder processBuilder = new ProcessBuilder(commandParts);
            processBuilder.redirectErrorStream(true);

            log.info("Starting process: {}", Arrays.toString(commandParts));
            Process process = processBuilder.start();

            // Enviar el mensaje al proceso via stdin
            try (PrintWriter writer = new PrintWriter(process.getOutputStream(), true)) {
                // Enviar mensaje en formato JSON para MCP
                String mcpMessage = createMcpMessage(message, selectedTool);
                writer.println(mcpMessage);
                writer.flush();
            }

            // Leer la respuesta del proceso via stdout
            StringBuilder response = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                long startTime = System.currentTimeMillis();
                long timeout = 10000; // 10 segundos timeout

                while ((line = reader.readLine()) != null) {
                    response.append(line).append("\n");

                    // Check timeout
                    if (System.currentTimeMillis() - startTime > timeout) {
                        log.warn("Timeout waiting for response from {}", server.getName());
                        break;
                    }

                    // Si recibimos una respuesta completa (esto depende del protocolo MCP)
                    if (isCompleteResponse(line)) {
                        break;
                    }
                }
            }

            // Esperar a que el proceso termine o timeout
            boolean finished = process.waitFor(5, TimeUnit.SECONDS);
            if (!finished) {
                log.warn("Process didn't finish within timeout, destroying it");
                process.destroyForcibly();
            }

            String responseText = response.toString().trim();
            if (responseText.isEmpty()) {
                return "No response received from " + server.getName();
            }

            // Parsear respuesta MCP si es JSON
            return parseMcpResponse(responseText, server.getName());

        } catch (IOException e) {
            log.error("IO error communicating with {}: {}", server.getName(), e.getMessage());
            return "IO error communicating with " + server.getName() + ": " + e.getMessage();
        } catch (InterruptedException e) {
            log.error("Process interrupted for {}: {}", server.getName(), e.getMessage());
            Thread.currentThread().interrupt();
            return "Process interrupted for " + server.getName();
        } catch (Exception e) {
            log.error("Unexpected error communicating with {}: {}", server.getName(), e.getMessage());
            return "Unexpected error communicating with " + server.getName() + ": " + e.getMessage();
        }
    }

    // Cambia "chat" por "search_movies" en handleHttpMessage y createMcpMessage

    private String handleHttpMessage(McpServer server, String message, Map<String, Object> selectedTool) {
        log.info("Handling HTTP message for server: {} with tool: {}", server.getName(),
                selectedTool != null ? selectedTool.get("name") : "default");

        try {
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("jsonrpc", "2.0");
            requestBody.put("id", UUID.randomUUID().toString());
            requestBody.put("method", "tools/call");

            Map<String, Object> params = new HashMap<>();

            // Usar la herramienta seleccionada dinámicamente
            String toolName = "search"; // valor por defecto
            if (selectedTool != null && selectedTool.get("name") != null) {
                toolName = (String) selectedTool.get("name");
            }
            params.put("name", toolName);

            // Extraer el nombre del personaje/consulta del mensaje del usuario
            String query = extractQueryFromMessage(message);

            Map<String, Object> arguments = new HashMap<>();
            arguments.put("query", query);
            arguments.put("timestamp", System.currentTimeMillis());
            params.put("arguments", arguments);

            requestBody.put("params", params);

            String jsonBody = convertToJson(requestBody);
            log.debug("Sending HTTP request to {}: {}", server.getUrl(), jsonBody);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(server.getUrl() + "/mcp"))
                    .timeout(Duration.ofSeconds(30))
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                    .build();

            HttpResponse<String> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofString());

            log.debug("HTTP response from {}: Status={}, Body={}",
                    server.getName(), response.statusCode(), response.body());

            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                String responseBody = response.body();
                log.info("HTTP response from {}: {}", server.getName(), responseBody);

                return parseMcpResponse(responseBody, server.getName());

            } else {
                String errorMsg = "Error: HTTP " + response.statusCode() + " from " + server.getName();
                if (response.body() != null && !response.body().isEmpty()) {
                    errorMsg += " - " + response.body();
                }
                return errorMsg;
            }

        } catch (IOException e) {
            log.error("IO error communicating with {}: {}", server.getName(), e.getMessage());
            return "Connection error with " + server.getName() + ": " + e.getMessage();
        } catch (InterruptedException e) {
            log.error("Request interrupted for {}: {}", server.getName(), e.getMessage());
            Thread.currentThread().interrupt();
            return "Request timeout for " + server.getName();
        } catch (Exception e) {
            log.error("Unexpected error with {}: {}", server.getName(), e.getMessage());
            return "Error communicating with " + server.getName() + ": " + e.getMessage();
        }
    }

    private String createMcpMessage(String userMessage, Map<String, Object> selectedTool) {
        try {
            Map<String, Object> mcpRequest = new HashMap<>();
            mcpRequest.put("jsonrpc", "2.0");
            mcpRequest.put("id", UUID.randomUUID().toString());
            mcpRequest.put("method", "tools/call");

            Map<String, Object> params = new HashMap<>();

            // Usar la herramienta seleccionada dinámicamente
            String toolName = "search"; // valor por defecto
            if (selectedTool != null && selectedTool.get("name") != null) {
                toolName = (String) selectedTool.get("name");
            }
            params.put("name", toolName);

            Map<String, Object> arguments = new HashMap<>();
            String query = extractQueryFromMessage(userMessage);
            arguments.put("query", query);
            params.put("arguments", arguments);

            mcpRequest.put("params", params);

            return convertToJson(mcpRequest);

        } catch (Exception e) {
            log.warn("Failed to create MCP JSON message, sending plain text: {}", e.getMessage());
            return userMessage;
        }
    }

    private String convertToJson(Map<String, Object> map) {
        // Implementación simple de JSON - en producción usar ObjectMapper
        StringBuilder json = new StringBuilder("{");
        boolean first = true;

        for (Map.Entry<String, Object> entry : map.entrySet()) {
            if (!first) json.append(",");
            json.append("\"").append(entry.getKey()).append("\":");

            Object value = entry.getValue();
            if (value instanceof String) {
                json.append("\"").append(escapeJson((String) value)).append("\"");
            } else if (value instanceof Map) {
                json.append(convertToJson((Map<String, Object>) value));
            } else if (value instanceof Number) {
                json.append(value);
            } else {
                json.append("\"").append(value).append("\"");
            }
            first = false;
        }
        json.append("}");
        return json.toString();
    }

    private String escapeJson(String str) {
        return str.replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t")
                .replace("\\", "\\\\");
    }

    private boolean isCompleteResponse(String line) {
        // Verificar si la línea indica una respuesta completa de MCP
        return line.contains("\"result\"") || line.contains("\"error\"") ||
                (line.trim().endsWith("}") && line.contains("\"jsonrpc\""));
    }

    private String parseMcpResponse(String responseText, String serverName) {
        try {
            log.debug("Parsing MCP response from {}: {}", serverName, responseText);

            Map<String, Object> json = objectMapper.readValue(responseText, Map.class);

            if (json.containsKey("result")) {
                Map<String, Object> result = (Map<String, Object>) json.get("result");
                // Manejar el caso de results vacío
                if (result.containsKey("results") && result.get("results") instanceof List<?> resultsList) {
                    if (resultsList.isEmpty()) {
                        return "No se encontraron resultados para la consulta.";
                    }
                }
                Object contentObj = result.get("content");
                if (contentObj instanceof List<?> contentList && !contentList.isEmpty()) {
                    StringBuilder sb = new StringBuilder();
                    for (Object item : contentList) {
                        if (item instanceof Map<?, ?> itemMap && itemMap.containsKey("text")) {
                            sb.append(itemMap.get("text")).append("\n");
                        }
                    }
                    return sb.toString().trim();
                } else if (contentObj instanceof String s) {
                    return s;
                }
            }
            if (json.containsKey("error")) {
                return "Error from " + serverName + ": " + objectMapper.writeValueAsString(json.get("error"));
            }
            return responseText;
        } catch (Exception e) {
            log.warn("Failed to parse MCP response from {}, returning raw response: {}", serverName, e.getMessage());
            return responseText;
        }
    }

    private String unescapeJson(String str) {
        return str.replace("\\\"", "\"")
                .replace("\\n", "\n")
                .replace("\\r", "\r")
                .replace("\\t", "\t")
                .replace("\\\\", "\\");
    }

    private String handleWebSocketMessage(McpServer server, String message) {
        log.info("Handling WebSocket message for server: {}", server.getName());
        // TODO: Implementar comunicación WebSocket real con el servidor MCP
        return "WebSocket communication with " + server.getName() + " is not yet implemented.";
    }

    private String handleTcpMessage(McpServer server, String message) {
        log.info("Handling TCP message for server: {}", server.getName());
        // TODO: Implementar comunicación TCP real con el servidor MCP
        return "TCP communication with " + server.getName() + " is not yet implemented.";
    }

    public List<String> getConversationIds() {
        return new ArrayList<>(conversations.keySet());
    }

    public void clearConversation(String conversationId) {
        conversations.remove(conversationId);
        log.info("Cleared conversation: {}", conversationId);
    }
}