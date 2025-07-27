package org.shark.mentor.mcp.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.shark.mentor.mcp.model.ChatMessage;
import org.shark.mentor.mcp.model.McpRequest;
import org.shark.mentor.mcp.model.McpServer;
import org.springframework.stereotype.Service;

import java.io.*;
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

    public List<ChatMessage> getConversation(String conversationId) {
        return conversations.getOrDefault(conversationId, new ArrayList<>());
    }

    public List<String> getConversationIds() {
        return new ArrayList<>(conversations.keySet());
    }

    public void clearConversation(String conversationId) {
        conversations.remove(conversationId);
        log.info("Cleared conversation: {}", conversationId);
    }

    public ChatMessage sendMessage(McpRequest request) {
        String conversationId = request.getConversationId();
        if (conversationId == null) {
            conversationId = "default";
        }

        Optional<McpServer> serverOpt = mcpServerService.getServer(request.getServerId());
        if (serverOpt.isEmpty()) {
            throw new IllegalArgumentException("Server not found: " + request.getServerId());
        }

        McpServer server = serverOpt.get();
        if (!"CONNECTED".equals(server.getStatus())) {
            throw new IllegalStateException("Server is not connected: " + server.getName());
        }

        ChatMessage userMessage = ChatMessage.builder()
                .id(UUID.randomUUID().toString())
                .role("USER")
                .content(request.getMessage())
                .timestamp(System.currentTimeMillis())
                .serverId(request.getServerId())
                .build();

        addMessageToConversation(conversationId, userMessage);

        ChatMessage contextMessage = ChatMessage.builder()
                .id(UUID.randomUUID().toString())
                .role("SYSTEM")
                .content("Contexto no implementado todavía")
                .timestamp(System.currentTimeMillis())
                .serverId(request.getServerId())
                .build();

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

    private List<Map<String, Object>> getDefaultToolsForServer(McpServer server) {
        return new ArrayList<>();
    }

    private String convertToJson(Map<String, Object> map) throws IOException {
        return objectMapper.writeValueAsString(map);
    }
}
