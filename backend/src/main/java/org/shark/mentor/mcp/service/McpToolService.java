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

@Service
@Slf4j
public class McpToolService {
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final McpServerService mcpServerService;

    public McpToolService(McpServerService mcpServerService) {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        this.mcpServerService = mcpServerService;
    }

    public List<Map<String, Object>> getTools(McpServer server) {
        log.info("Fetching tools for server: {}", server.getName());
        String protocol = extractProtocol(server.getUrl());
        List<Map<String, Object>> tools;
        if ("stdio".equalsIgnoreCase(protocol)) {
            tools = getToolsViaStdio(server);
        } else {
            tools = getToolsViaHttp(server);
        }
        // Normaliza la clave input_schema a inputSchema para compatibilidad
        for (Map<String, Object> tool : tools) {
            if (tool.containsKey("input_schema")) {
                tool.put("inputSchema", tool.get("input_schema"));
            }
        }
        log.info("Tools encontradas para el servidor {}: {}", server.getName(), tools);
        return tools;
    }

    private String extractProtocol(String url) {
        if (url == null || !url.contains("://")) {
            return "http";
        }
        return url.substring(0, url.indexOf("://"));
    }

    private List<Map<String, Object>> getToolsViaStdio(McpServer server) {
        log.debug("Attempting to fetch tools via stdio for server: {}", server.getName());
        try {
            OutputStream stdin = mcpServerService.getStdioInput(server.getId());
            InputStream stdout = mcpServerService.getStdioOutput(server.getId());

            if (stdin == null || stdout == null) {
                log.warn("No stdio streams available for server: {}", server.getName());
                return Collections.emptyList();
            }

            Map<String, Object> request = new HashMap<>();
            request.put("jsonrpc", "2.0");
            request.put("id", UUID.randomUUID().toString());
            request.put("method", "tools/list");
            request.put("params", new HashMap<>());

            String jsonRequest = objectMapper.writeValueAsString(request);
            log.debug("Sending tools/list via stdio to {}: {}", server.getName(), jsonRequest);

            stdin.write((jsonRequest + "\n").getBytes(StandardCharsets.UTF_8));
            stdin.flush();

            BufferedReader reader = new BufferedReader(new InputStreamReader(stdout, StandardCharsets.UTF_8));

            for (int i = 0; i < 10; i++) {
                String line = reader.readLine();
                if (line == null) break;

                line = line.trim();
                if (line.isEmpty()) continue;

                log.debug("Stdio response received from {}: {}", server.getName(), line);

                try {
                    JsonNode responseNode = objectMapper.readTree(line);
                    if (responseNode.has("result")) {
                        JsonNode result = responseNode.get("result");
                        if (result.has("tools")) {
                            JsonNode toolsNode = result.get("tools");
                            if (toolsNode.isArray()) {
                                List<Map<String, Object>> tools = objectMapper.convertValue(toolsNode, List.class);
                                log.info("Retrieved {} tools via stdio from {}", tools.size(), server.getName());
                                return tools;
                            }
                        }
                    } else if (responseNode.has("error")) {
                        log.warn("Error response from stdio tools/list in {}: {}", server.getName(), responseNode.get("error"));
                        break;
                    }
                } catch (Exception e) {
                    log.debug("Line is not valid JSON, continuing: {}", line);
                }
            }

        } catch (Exception e) {
            log.warn("Failed to obtain tools via stdio from {}: {}", server.getName(), e.getMessage());
        }

        return Collections.emptyList();
    }

    private List<Map<String, Object>> getToolsViaHttp(McpServer server) {
        log.debug("Attempting to fetch tools via HTTP for server: {}", server.getName());
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
            log.debug("Raw tools/list response from {}: {}", server.getName(), responseText);

            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                JsonNode rootNode = objectMapper.readTree(responseText);
                if (!rootNode.isObject()) {
                    log.warn("Expected JSON object from tools/list, but got: {}", rootNode);
                    return Collections.emptyList();
                }
                if (rootNode.has("result") && rootNode.get("result").has("tools")) {
                    JsonNode toolsNode = rootNode.get("result").get("tools");
                    if (toolsNode.isArray()) {
                        List<Map<String, Object>> tools = objectMapper.convertValue(toolsNode, List.class);
                        log.info("Retrieved {} tools via HTTP from {}", tools.size(), server.getName());
                        return tools;
                    }
                }
            } else {
                log.warn("Error HTTP desde tools/list en {}: status={}, body={}",
                        server.getName(), response.statusCode(), responseText);
            }
        } catch (Exception e) {
            log.warn("Failed to obtain tools via HTTP from {}: {}", server.getName(), e.getMessage());
        }
        return Collections.emptyList();
    }

    public String selectBestTool(String message, McpServer server) {
        log.info("Selecting best tool for message: '{}' on server: {}", message, server.getName());
        List<Map<String, Object>> tools = getTools(server);
        String lower = message.toLowerCase();
        for (Map<String, Object> tool : tools) {
            Object nameObj = tool.get("name");
            if (nameObj instanceof String) {
                String name = ((String) nameObj).toLowerCase();
                if (lower.contains(name)) {
                    log.info("Tool selected by name: {}", nameObj);
                    return (String) nameObj;
                }
            }
            Object descObj = tool.get("description");
            if (descObj instanceof String) {
                String description = ((String) descObj).toLowerCase();
                for (String word : description.split("\\W+")) {
                    if (word.length() > 3 && lower.contains(word)) {
                        log.info("Tool selected by description: {}", tool.get("name"));
                        return (String) tool.get("name");
                    }
                }
            }
        }
        String fallback = tools.isEmpty() ? null : (String) tools.get(0).get("name");
        log.info("Tool selected by fallback: {}", fallback);
        return fallback;
    }

    public Map<String, Object> extractToolArguments(String message, String toolName, Map<String, Object> inputSchema) {
        log.info("Extracting arguments for tool '{}' from message: '{}'", toolName, message);
        Map<String, Object> args = new HashMap<>();
        switch (toolName) {
            case "list_repositories":
                if (message.toLowerCase().contains("publico") || message.toLowerCase().contains("public")) {
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
        log.debug("Extracted arguments: {}", args);
        if (inputSchema != null) {
            List<String> validationErrors = validateArgumentsAgainstSchema(inputSchema, args);
            if (!validationErrors.isEmpty()) {
                log.warn("Extracted arguments do not satisfy schema: {}", validationErrors);
            }
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

    private Map<String, Object> getInputSchema(McpServer server, String toolName) {
        return getTools(server).stream()
                .filter(t -> toolName.equals(t.get("name")))
                .findFirst()
                .map(t -> (Map<String, Object>) t.get("inputSchema"))
                .orElse(null);
    }

    private List<String> validateArgumentsAgainstSchema(Map<String, Object> inputSchema, Map<String, Object> arguments) {
        List<String> errors = new ArrayList<>();
        if (inputSchema == null) {
            return errors;
        }
        Map<String, Object> properties = (Map<String, Object>) inputSchema.get("properties");
        List<String> required = (List<String>) inputSchema.get("required");
        if (required != null) {
            for (String field : required) {
                if (!arguments.containsKey(field)) {
                    errors.add("Missing required field: " + field);
                }
            }
        }
        if (properties != null) {
            for (Map.Entry<String, Object> entry : properties.entrySet()) {
                String field = entry.getKey();
                Object schemaDef = entry.getValue();
                if (!(schemaDef instanceof Map)) {
                    continue;
                }
                Map<String, Object> propertySchema = (Map<String, Object>) schemaDef;
                Object typeObj = propertySchema.get("type");
                if (typeObj == null) {
                    continue;
                }
                String expectedType = typeObj.toString();
                if (arguments.containsKey(field)) {
                    Object value = arguments.get(field);
                    if (!matchesType(value, expectedType)) {
                        errors.add("Field '" + field + "' should be of type " + expectedType);
                    }
                }
            }
        }
        return errors;
    }

    private boolean matchesType(Object value, String expectedType) {
        return switch (expectedType) {
            case "string" -> value instanceof String;
            case "integer" -> value instanceof Integer || value instanceof Long;
            case "number" -> value instanceof Number;
            case "boolean" -> value instanceof Boolean;
            case "object" -> value instanceof Map;
            case "array" -> value instanceof List;
            default -> true;
        };
    }

    private String buildValidationError(List<String> errors) {
        try {
            Map<String, Object> error = Map.of(
                    "error", Map.of(
                            "code", 400,
                            "message", "Invalid arguments",
                            "details", errors
                    )
            );
            return objectMapper.writeValueAsString(error);
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize validation error", e);
        }
    }

    // MCP compliant: POST a /mcp con JSON-RPC tools/call
    public String callToolViaHttp(McpServer server, String toolName, Map<String, Object> arguments) throws IOException, InterruptedException {
        log.info("Calling tool '{}' via HTTP on server: {} with arguments: {}", toolName, server.getName(), arguments);
        Map<String, Object> inputSchema = getInputSchema(server, toolName);
        List<String> validationErrors = validateArgumentsAgainstSchema(inputSchema, arguments);
        if (!validationErrors.isEmpty()) {
            log.warn("Argument validation failed: {}", validationErrors);
            return buildValidationError(validationErrors);
        }
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
        log.debug("Sending tool call via HTTP: {}", json);
        HttpRequest httpRequest = HttpRequest.newBuilder()
                .uri(URI.create(server.getUrl() + "/mcp"))
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();
        HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
        log.debug("Respuesta de llamada HTTP: {}", response.body());
        return response.body();
    }

    public String callToolViaStdio(McpServer server, OutputStream stdin, InputStream stdout, String toolName, Map<String, Object> arguments) throws IOException {
        log.info("Calling tool '{}' via stdio with arguments: {}", toolName, arguments);
        Map<String, Object> inputSchema = getInputSchema(server, toolName);
        List<String> validationErrors = validateArgumentsAgainstSchema(inputSchema, arguments);
        if (!validationErrors.isEmpty()) {
            log.warn("Argument validation failed: {}", validationErrors);
            return buildValidationError(validationErrors);
        }
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
        log.debug("Sending tool call via stdio: {}", json);
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
            log.warn("Content-Length not found in stdio response");
            return null;
        }
        char[] buf = new char[contentLength];
        int read = 0;
        while (read < contentLength) {
            int n = reader.read(buf, read, contentLength - read);
            if (n == -1) break;
            read += n;
        }
        String result = new String(buf, 0, read);
        log.debug("Respuesta de llamada stdio: {}", result);
        return result;
    }

    // Método genérico para invocar cualquier método MCP vía HTTP
    public String callMcpMethodViaHttp(McpServer server, String method, Map<String, Object> params) throws IOException, InterruptedException {
        log.info("Calling MCP method '{}' via HTTP on server: {} with params: {}", method, server.getName(), params);
        Map<String, Object> mcpCall = Map.of(
                "jsonrpc", "2.0",
                "id", UUID.randomUUID().toString(),
                "method", method,
                "params", params != null ? params : new HashMap<>()
        );
        String json = objectMapper.writeValueAsString(mcpCall);
        log.debug("Sending MCP method call via HTTP: {}", json);
        HttpRequest httpRequest = HttpRequest.newBuilder()
                .uri(URI.create(server.getUrl() + "/mcp"))
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();
        HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
        log.debug("Respuesta de llamada HTTP: {}", response.body());
        return response.body();
    }
}