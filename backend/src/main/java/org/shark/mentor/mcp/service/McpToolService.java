package org.shark.mentor.mcp.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.shark.mentor.mcp.model.McpServer;
import org.springframework.stereotype.Service;

import java.io.*;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;

/**
 * Servicio dedicado a la gestión de tools MCP: descubrimiento, selección, argumentos y llamada
 */
@Service
@Slf4j
public class McpToolService {
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public McpToolService() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    public List<Map<String, Object>> getTools(McpServer server) {
        try {
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("jsonrpc", "2.0");
            requestBody.put("id", UUID.randomUUID().toString());
            requestBody.put("method", "tools/list");
            requestBody.put("params", new HashMap<>());

            String jsonBody = objectMapper.writeValueAsString(requestBody);
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
                    return Collections.emptyList();
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
        return Collections.emptyList();
    }

    public String selectBestTool(String message, McpServer server) {
        List<Map<String, Object>> tools = getTools(server);
        String lower = message.toLowerCase();
        for (Map<String, Object> tool : tools) {
            Object nameObj = tool.get("name");
            if (nameObj instanceof String) {
                String name = ((String) nameObj).toLowerCase();
                if (lower.contains(name)) {
                    return (String) nameObj;
                }
            }
            Object descObj = tool.get("description");
            if (descObj instanceof String) {
                String description = ((String) descObj).toLowerCase();
                for (String word : description.split("\\W+")) {
                    if (word.length() > 3 && lower.contains(word)) {
                        return (String) tool.get("name");
                    }
                }
            }
        }
        return tools.isEmpty() ? null : (String) tools.get(0).get("name");
    }

    public Map<String, Object> extractToolArguments(String message, String toolName) {
        Map<String, Object> args = new HashMap<>();
        switch (toolName) {
            case "list_repositories":
                if (message.toLowerCase().contains("público") || message.toLowerCase().contains("public")) {
                    args.put("type", "public");
                }
                if (message.toLowerCase().contains("privado") || message.toLowerCase().contains("private")) {
                    args.put("type", "private");
                }
                break;
            case "search_repositories":
                String searchTerm = extractSearchTerm(message);
                if (!searchTerm.isEmpty()) {
                    args.put("q", searchTerm);
                }
                break;
            case "get_file_contents":
                extractRepoInfo(message, args);
                break;
        }
        return args;
    }

    private String extractSearchTerm(String message) {
        String[] keywords = {"buscar", "search", "encuentra", "find"};
        for (String keyword : keywords) {
            int index = message.toLowerCase().indexOf(keyword);
            if (index != -1) {
                String remaining = message.substring(index + keyword.length()).trim();
                return remaining.split("\\s+")[0];
            }
        }
        return "";
    }

    private void extractRepoInfo(String message, Map<String, Object> args) {
        if (message.contains("/")) {
            String[] parts = message.split("/");
            if (parts.length >= 2) {
                args.put("owner", parts[0].trim());
                args.put("repo", parts[1].trim());
            }
        }
    }

    public String callToolViaHttp(McpServer server, String toolName, Map<String, Object> arguments) throws IOException, InterruptedException {
        Map<String, Object> toolCall = Map.of(
                "jsonrpc", "2.0",
                "id", UUID.randomUUID().toString(),
                "method", "tools/call",
                "params", Map.of(
                        "name", toolName,
                        "arguments", arguments
                )
        );
        String json = objectMapper.writeValueAsString(toolCall);
        log.info("Enviando llamada de herramienta por HTTP: {}", json);
        HttpRequest httpRequest = HttpRequest.newBuilder()
                .uri(URI.create(server.getUrl() + "/mcp"))
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();
        HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
        return response.body();
    }

    public String callToolViaStdio(OutputStream stdin, InputStream stdout, String toolName, Map<String, Object> arguments) throws IOException {
        Map<String, Object> toolCall = Map.of(
                "jsonrpc", "2.0",
                "id", UUID.randomUUID().toString(),
                "method", "tools/call",
                "params", Map.of(
                        "name", toolName,
                        "arguments", arguments
                )
        );
        String json = objectMapper.writeValueAsString(toolCall);
        log.info("Enviando llamada de herramienta por stdio: {}", json);
        byte[] jsonBytes = json.getBytes(StandardCharsets.UTF_8);
        String header = "Content-Length: " + jsonBytes.length + "\r\n\r\n";
        stdin.write(header.getBytes(StandardCharsets.UTF_8));
        stdin.write(jsonBytes);
        stdin.flush();

        BufferedReader reader = new BufferedReader(new InputStreamReader(stdout, StandardCharsets.UTF_8));
        int contentLength = -1;
        String line;
        while ((line = reader.readLine()) != null) {
            if (line.isEmpty()) {
                break;
            }
            if (line.toLowerCase().startsWith("content-length:")) {
                contentLength = Integer.parseInt(line.substring("content-length:".length()).trim());
            }
        }
        if (contentLength < 0) {
            return null;
        }
        char[] buf = new char[contentLength];
        int read = 0;
        while (read < contentLength) {
            int n = reader.read(buf, read, contentLength - read);
            if (n == -1) break;
            read += n;
        }
        return new String(buf, 0, read);
    }
}

