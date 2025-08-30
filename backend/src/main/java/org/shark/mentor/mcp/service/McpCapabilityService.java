package org.shark.mentor.mcp.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.shark.mentor.mcp.model.McpCapabilities;
import org.shark.mentor.mcp.model.McpServer;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.UUID;

@Service
@Slf4j
public class McpCapabilityService {
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public McpCapabilityService() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    public McpCapabilities discoverCapabilities(McpServer server) {
        log.info("Discovering capabilities for server: {}", server.getName());
        String protocol = extractProtocol(server.getUrl());
        if ("stdio".equalsIgnoreCase(protocol)) {
            return discoverCapabilitiesViaStdio(server);
        } else {
            return discoverCapabilitiesViaHttp(server);
        }
    }

    private String extractProtocol(String url) {
        if (url == null || !url.contains("://")) {
            return "http";
        }
        return url.substring(0, url.indexOf("://"));
    }

    private McpCapabilities discoverCapabilitiesViaHttp(McpServer server) {
        try {
            String initUrl = server.getUrl() + "/mcp/initialize";
            String body = String.format(
                "{\"jsonrpc\":\"2.0\",\"id\":\"%s\",\"method\":\"initialize\",\"params\":{\"protocolVersion\":\"2025-06-18\",\"capabilities\":{\"tools\":{},\"resources\":{},\"prompts\":{},\"logging\":{}}}}",
                UUID.randomUUID());
            
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(initUrl))
                    .timeout(Duration.ofSeconds(10))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                return parseCapabilitiesResponse(response.body());
            } else {
                log.warn("Failed to discover capabilities from {}: HTTP {}", server.getName(), response.statusCode());
                return getDefaultCapabilities();
            }
        } catch (Exception e) {
            log.error("Error discovering capabilities from {}: {}", server.getName(), e.getMessage());
            return getDefaultCapabilities();
        }
    }

    private McpCapabilities discoverCapabilitiesViaStdio(McpServer server) {
        log.warn("STDIO capabilities discovery not yet implemented for server: {}", server.getName());
        return getDefaultCapabilities();
    }

    private McpCapabilities parseCapabilitiesResponse(String responseBody) {
        try {
            JsonNode jsonNode = objectMapper.readTree(responseBody);
            JsonNode result = jsonNode.get("result");
            
            if (result != null && result.has("capabilities")) {
                JsonNode capabilities = result.get("capabilities");
                
                return McpCapabilities.builder()
                        .resources(capabilities.has("resources"))
                        .tools(capabilities.has("tools"))
                        .prompts(capabilities.has("prompts"))
                        .logging(capabilities.has("logging"))
                        .sampling(capabilities.has("sampling"))
                        .experimental(capabilities.get("experimental"))
                        .build();
            }
        } catch (Exception e) {
            log.error("Failed to parse capabilities response: {}", e.getMessage());
        }
        return getDefaultCapabilities();
    }

    private McpCapabilities getDefaultCapabilities() {
        return McpCapabilities.builder()
                .tools(true)
                .resources(false)
                .prompts(false)
                .logging(false)
                .sampling(false)
                .build();
    }
}