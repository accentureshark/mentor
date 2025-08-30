package org.shark.mentor.mcp.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.shark.mentor.mcp.model.McpPrompt;
import org.shark.mentor.mcp.model.McpServer;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;

@Service
@Slf4j
public class McpPromptService {
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final McpServerService mcpServerService;

    public McpPromptService(McpServerService mcpServerService) {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        this.mcpServerService = mcpServerService;
    }

    public List<McpPrompt> getPrompts(McpServer server) {
        log.info("Fetching prompts for server: {}", server.getName());
        String protocol = extractProtocol(server.getUrl());
        if ("stdio".equalsIgnoreCase(protocol)) {
            return getPromptsViaStdio(server);
        } else {
            return getPromptsViaHttp(server);
        }
    }

    public String getPrompt(McpServer server, String name, Map<String, Object> arguments) {
        log.info("Getting prompt {} from server: {}", name, server.getName());
        String protocol = extractProtocol(server.getUrl());
        if ("stdio".equalsIgnoreCase(protocol)) {
            return getPromptViaStdio(server, name, arguments);
        } else {
            return getPromptViaHttp(server, name, arguments);
        }
    }

    private String extractProtocol(String url) {
        if (url == null || !url.contains("://")) {
            return "http";
        }
        return url.substring(0, url.indexOf("://"));
    }

    private List<McpPrompt> getPromptsViaHttp(McpServer server) {
        try {
            String promptsUrl = server.getUrl() + "/mcp/prompts/list";
            String body = String.format("{\"jsonrpc\":\"2.0\",\"id\":\"%s\",\"method\":\"prompts/list\",\"params\":{}}", 
                    UUID.randomUUID());
            
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(promptsUrl))
                    .timeout(Duration.ofSeconds(10))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                return parsePromptsResponse(response.body());
            } else {
                log.warn("Failed to get prompts from {}: HTTP {}", server.getName(), response.statusCode());
                return Collections.emptyList();
            }
        } catch (Exception e) {
            log.error("Error fetching prompts from {}: {}", server.getName(), e.getMessage());
            return Collections.emptyList();
        }
    }

    private List<McpPrompt> getPromptsViaStdio(McpServer server) {
        log.warn("STDIO prompt fetching not yet implemented for server: {}", server.getName());
        return Collections.emptyList();
    }

    private String getPromptViaHttp(McpServer server, String name, Map<String, Object> arguments) {
        try {
            String promptUrl = server.getUrl() + "/mcp/prompts/get";
            
            Map<String, Object> params = new HashMap<>();
            params.put("name", name);
            if (arguments != null && !arguments.isEmpty()) {
                params.put("arguments", arguments);
            }
            
            String body = String.format("{\"jsonrpc\":\"2.0\",\"id\":\"%s\",\"method\":\"prompts/get\",\"params\":%s}", 
                    UUID.randomUUID(), objectMapper.writeValueAsString(params));
            
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(promptUrl))
                    .timeout(Duration.ofSeconds(10))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                return parsePromptResponse(response.body());
            } else {
                log.warn("Failed to get prompt {} from {}: HTTP {}", name, server.getName(), response.statusCode());
                return null;
            }
        } catch (Exception e) {
            log.error("Error getting prompt {} from {}: {}", name, server.getName(), e.getMessage());
            return null;
        }
    }

    private String getPromptViaStdio(McpServer server, String name, Map<String, Object> arguments) {
        log.warn("STDIO prompt getting not yet implemented for server: {}", server.getName());
        return null;
    }

    private List<McpPrompt> parsePromptsResponse(String responseBody) {
        try {
            JsonNode jsonNode = objectMapper.readTree(responseBody);
            JsonNode result = jsonNode.get("result");
            
            if (result != null && result.has("prompts")) {
                JsonNode promptsNode = result.get("prompts");
                List<McpPrompt> prompts = new ArrayList<>();
                
                if (promptsNode.isArray()) {
                    for (JsonNode promptNode : promptsNode) {
                        McpPrompt prompt = McpPrompt.builder()
                                .name(getTextValue(promptNode, "name"))
                                .description(getTextValue(promptNode, "description"))
                                .arguments(promptNode.get("arguments"))
                                .build();
                        prompts.add(prompt);
                    }
                }
                return prompts;
            }
        } catch (Exception e) {
            log.error("Failed to parse prompts response: {}", e.getMessage());
        }
        return Collections.emptyList();
    }

    private String parsePromptResponse(String responseBody) {
        try {
            JsonNode jsonNode = objectMapper.readTree(responseBody);
            JsonNode result = jsonNode.get("result");
            
            if (result != null && result.has("messages")) {
                JsonNode messagesNode = result.get("messages");
                if (messagesNode.isArray() && messagesNode.size() > 0) {
                    // Return the first message content
                    JsonNode firstMessage = messagesNode.get(0);
                    if (firstMessage.has("content")) {
                        JsonNode content = firstMessage.get("content");
                        if (content.has("text")) {
                            return content.get("text").asText();
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.error("Failed to parse prompt response: {}", e.getMessage());
        }
        return null;
    }

    private String getTextValue(JsonNode node, String fieldName) {
        JsonNode field = node.get(fieldName);
        return field != null ? field.asText() : null;
    }
}