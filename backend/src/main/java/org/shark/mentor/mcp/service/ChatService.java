package org.shark.mentor.mcp.service;

import org.shark.mentor.mcp.model.ChatMessage;
import org.shark.mentor.mcp.model.McpRequest;
import org.shark.mentor.mcp.model.McpServer;
import org.shark.mentor.mcp.service.LlmService;
import org.springframework.stereotype.Service;
import lombok.extern.slf4j.Slf4j;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.*;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Service for handling chat messages and MCP communication
 */
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

    public List<ChatMessage> getConversation(String conversationId) {
        return conversations.getOrDefault(conversationId, new ArrayList<>());
    }

    public ChatMessage sendMessage(McpRequest request) {
        String conversationId = request.getConversationId();
        if (conversationId == null) {
            conversationId = "default";
        }

        // Verificar que el servidor existe y está conectado
        Optional<McpServer> serverOpt = mcpServerService.getServer(request.getServerId());
        if (serverOpt.isEmpty()) {
            throw new IllegalArgumentException("Server not found: " + request.getServerId());
        }

        McpServer server = serverOpt.get();
        if (!"CONNECTED".equals(server.getStatus())) {
            throw new IllegalStateException("Server is not connected: " + server.getName());
        }

        // Add user message
        ChatMessage userMessage = ChatMessage.builder()
            .id(UUID.randomUUID().toString())
            .role("USER")
            .content(request.getMessage())
            .timestamp(System.currentTimeMillis())
            .serverId(request.getServerId())
            .build();

        addMessageToConversation(conversationId, userMessage);

        // Get additional context from the connected MCP server
        ChatMessage contextMessage = communicateWithMcpServer(request, server);

        // Generate the final response using the configured LLM
        String llmOutput = llmService.generate(request.getMessage(), contextMessage.getContent());
        ChatMessage assistantMessage = ChatMessage.builder()
            .id(UUID.randomUUID().toString())
            .role("ASSISTANT")
            .content(llmOutput)
            .timestamp(System.currentTimeMillis())
            .serverId(request.getServerId())
            .build();

        addMessageToConversation(conversationId, assistantMessage);

        return assistantMessage;
    }

    private void addMessageToConversation(String conversationId, ChatMessage message) {
        conversations.computeIfAbsent(conversationId, k -> new ArrayList<>()).add(message);
        log.info("Added message to conversation {}: {}", conversationId, message.getContent());
    }

    private ChatMessage communicateWithMcpServer(McpRequest request, McpServer server) {
        log.info("Communicating with MCP server: {} for message: {}", server.getName(), request.getMessage());

        try {
            String response = switch (extractProtocol(server.getUrl())) {
                case "stdio" -> handleStdioMessage(server, request.getMessage());
                case "http", "https" -> handleHttpMessage(server, request.getMessage());
                case "ws", "wss" -> handleWebSocketMessage(server, request.getMessage());
                case "tcp" -> handleTcpMessage(server, request.getMessage());
                default -> "Error: Unsupported protocol for server " + server.getName();
            };

            return ChatMessage.builder()
                .id(UUID.randomUUID().toString())
                .role("ASSISTANT")
                .content(response)
                .timestamp(System.currentTimeMillis())
                .serverId(request.getServerId())
                .build();

        } catch (Exception e) {
            log.error("Error communicating with MCP server {}: {}", server.getName(), e.getMessage());

            return ChatMessage.builder()
                .id(UUID.randomUUID().toString())
                .role("ASSISTANT")
                .content("Sorry, I encountered an error while communicating with " + server.getName() + ": " + e.getMessage())
                .timestamp(System.currentTimeMillis())
                .serverId(request.getServerId())
                .build();
        }
    }

    private String extractProtocol(String url) {
        if (url == null || !url.contains("://")) {
            return "unknown";
        }
        return url.substring(0, url.indexOf("://"));
    }

    private String handleStdioMessage(McpServer server, String message) {
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
                String mcpMessage = createMcpMessage(message);
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

    private String handleHttpMessage(McpServer server, String message) {
        log.info("Handling HTTP message for server: {}", server.getName());

        try {
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("jsonrpc", "2.0");
            requestBody.put("id", UUID.randomUUID().toString());
            requestBody.put("method", "tools/call");

            Map<String, Object> params = new HashMap<>();
            params.put("name", "search_movies"); // Cambiado de "chat" a "search_movies"

            Map<String, Object> arguments = new HashMap<>();
            arguments.put("query", message); // Cambia "message" por "query" si la herramienta lo requiere
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

    private String createMcpMessage(String userMessage) {
        try {
            Map<String, Object> mcpRequest = new HashMap<>();
            mcpRequest.put("jsonrpc", "2.0");
            mcpRequest.put("id", UUID.randomUUID().toString());
            mcpRequest.put("method", "tools/call");

            Map<String, Object> params = new HashMap<>();
            params.put("name", "search_movies"); // Cambiado de "chat" a "search_movies"

            Map<String, Object> arguments = new HashMap<>();
            arguments.put("query", userMessage); // Cambia "message" por "query" si la herramienta lo requiere
            params.put("arguments", arguments);

            mcpRequest.put("params", params);

            return convertToJson(mcpRequest);

        } catch (Exception e) {
            log.warn("Failed to create MCP JSON message, sending plain text: {}", e.getMessage());
            return userMessage;
        }
    }

    private String convertToJson(Map<String, Object> map) {
        try {
            return objectMapper.writeValueAsString(map);
        } catch (Exception e) {
            log.error("Failed to convert map to JSON", e);
            return "{}";
        }
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
                        if (item instanceof Map<?,?> itemMap && itemMap.containsKey("text")) {
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