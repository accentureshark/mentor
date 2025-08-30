package org.shark.mentor.mcp.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.shark.mentor.mcp.model.McpResource;
import org.shark.mentor.mcp.model.McpServer;
import org.springframework.stereotype.Service;

import java.io.*;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;

@Service
@Slf4j
public class McpResourceService {
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final McpServerService mcpServerService;

    public McpResourceService(McpServerService mcpServerService) {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        this.mcpServerService = mcpServerService;
    }

    public List<McpResource> getResources(McpServer server) {
        log.info("Fetching resources for server: {}", server.getName());
        String protocol = extractProtocol(server.getUrl());
        if ("stdio".equalsIgnoreCase(protocol)) {
            return getResourcesViaStdio(server);
        } else {
            return getResourcesViaHttp(server);
        }
    }

    public String readResource(McpServer server, String uri) {
        log.info("Reading resource {} from server: {}", uri, server.getName());
        String protocol = extractProtocol(server.getUrl());
        if ("stdio".equalsIgnoreCase(protocol)) {
            return readResourceViaStdio(server, uri);
        } else {
            return readResourceViaHttp(server, uri);
        }
    }

    private String extractProtocol(String url) {
        if (url == null || !url.contains("://")) {
            return "http";
        }
        return url.substring(0, url.indexOf("://"));
    }

    private List<McpResource> getResourcesViaHttp(McpServer server) {
        try {
            String resourcesUrl = server.getUrl() + "/mcp/resources/list";
            String body = String.format("{\"jsonrpc\":\"2.0\",\"id\":\"%s\",\"method\":\"resources/list\",\"params\":{}}", 
                    UUID.randomUUID());
            
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(resourcesUrl))
                    .timeout(Duration.ofSeconds(10))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                return parseResourcesResponse(response.body());
            } else {
                log.warn("Failed to get resources from {}: HTTP {}", server.getName(), response.statusCode());
                return Collections.emptyList();
            }
        } catch (Exception e) {
            log.error("Error fetching resources from {}: {}", server.getName(), e.getMessage());
            return Collections.emptyList();
        }
    }

    private List<McpResource> getResourcesViaStdio(McpServer server) {
        // For stdio servers, we'd need to send JSON-RPC over stdin/stdout
        log.warn("STDIO resource fetching not yet implemented for server: {}", server.getName());
        return Collections.emptyList();
    }

    private String readResourceViaHttp(McpServer server, String uri) {
        try {
            String resourceUrl = server.getUrl() + "/mcp/resources/read";
            String body = String.format("{\"jsonrpc\":\"2.0\",\"id\":\"%s\",\"method\":\"resources/read\",\"params\":{\"uri\":\"%s\"}}", 
                    UUID.randomUUID(), uri);
            
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(resourceUrl))
                    .timeout(Duration.ofSeconds(10))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                return parseResourceContentResponse(response.body());
            } else {
                log.warn("Failed to read resource {} from {}: HTTP {}", uri, server.getName(), response.statusCode());
                return null;
            }
        } catch (Exception e) {
            log.error("Error reading resource {} from {}: {}", uri, server.getName(), e.getMessage());
            return null;
        }
    }

    private String readResourceViaStdio(McpServer server, String uri) {
        log.warn("STDIO resource reading not yet implemented for server: {}", server.getName());
        return null;
    }

    private List<McpResource> parseResourcesResponse(String responseBody) {
        try {
            JsonNode jsonNode = objectMapper.readTree(responseBody);
            JsonNode result = jsonNode.get("result");
            
            if (result != null && result.has("resources")) {
                JsonNode resourcesNode = result.get("resources");
                List<McpResource> resources = new ArrayList<>();
                
                if (resourcesNode.isArray()) {
                    for (JsonNode resourceNode : resourcesNode) {
                        McpResource resource = McpResource.builder()
                                .uri(getTextValue(resourceNode, "uri"))
                                .name(getTextValue(resourceNode, "name"))
                                .description(getTextValue(resourceNode, "description"))
                                .mimeType(getTextValue(resourceNode, "mimeType"))
                                .annotations(resourceNode.get("annotations"))
                                .build();
                        resources.add(resource);
                    }
                }
                return resources;
            }
        } catch (Exception e) {
            log.error("Failed to parse resources response: {}", e.getMessage());
        }
        return Collections.emptyList();
    }

    private String parseResourceContentResponse(String responseBody) {
        try {
            JsonNode jsonNode = objectMapper.readTree(responseBody);
            JsonNode result = jsonNode.get("result");
            
            if (result != null && result.has("contents")) {
                JsonNode contentsNode = result.get("contents");
                if (contentsNode.isArray() && contentsNode.size() > 0) {
                    JsonNode firstContent = contentsNode.get(0);
                    if (firstContent.has("text")) {
                        return firstContent.get("text").asText();
                    }
                }
            }
        } catch (Exception e) {
            log.error("Failed to parse resource content response: {}", e.getMessage());
        }
        return null;
    }

    private String getTextValue(JsonNode node, String fieldName) {
        JsonNode field = node.get(fieldName);
        return field != null ? field.asText() : null;
    }
}