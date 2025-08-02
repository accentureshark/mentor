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

@Service
@Slf4j
public class ChatService {

    private final Map<String, List<ChatMessage>> conversations = new ConcurrentHashMap<>();
    private final McpServerService mcpServerService;
    private final LlmService llmService;
    private final McpToolService mcpToolService;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    // Optional dependencies for simplified implementation
    private final McpToolOrchestrator mcpToolOrchestrator;
    private final LlmServiceEnhanced enhancedLlmService;
    private final boolean useSimplifiedImplementation;

    public ChatService(McpServerService mcpServerService, 
                      LlmService llmService,
                      Optional<McpToolOrchestrator> mcpToolOrchestrator,
                      Optional<LlmServiceEnhanced> enhancedLlmService,
                      McpToolService mcpToolService) {
        this.mcpServerService = mcpServerService;
        this.llmService = llmService;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        this.mcpToolService = mcpToolService;

        // Use simplified implementation if available
        this.mcpToolOrchestrator = mcpToolOrchestrator.orElse(null);
        this.enhancedLlmService = enhancedLlmService.orElse(null);
        this.useSimplifiedImplementation = this.mcpToolOrchestrator != null && this.enhancedLlmService != null;
        
        if (useSimplifiedImplementation) {
            log.info("Using simplified langchain4j-based implementation");
        } else {
            log.info("Using original implementation");
        }
    }

    public List<ChatMessage> getConversation(String conversationId) {
        return conversations.getOrDefault(conversationId, new ArrayList<>());
    }

    public List<String> getConversationIds() {
        return new ArrayList<>(conversations.keySet());
    }

    public void clearConversation(String conversationId) {
        conversations.remove(conversationId);
        
        // Also clear from enhanced service if available
        if (useSimplifiedImplementation && enhancedLlmService != null) {
            enhancedLlmService.clearConversation(conversationId);
        }
        
        log.info("Cleared conversation: {}", conversationId);
    }

    public ChatMessage sendMessage(McpRequest request) {
        // Use simplified implementation if available
        if (useSimplifiedImplementation) {
            return sendMessageSimplified(request);
        } else {
            return sendMessageOriginal(request);
        }
    }

    private ChatMessage sendMessageSimplified(McpRequest request) {
        String conversationId = request.getConversationId();
        if (conversationId == null) {
            conversationId = "default";
        }

        // Validate server exists and is connected
        Optional<McpServer> serverOpt = mcpServerService.getServer(request.getServerId());
        if (serverOpt.isEmpty()) {
            return createErrorMessage(request, "Server not found: " + request.getServerId());
        }

        McpServer server = serverOpt.get();
        if (!"CONNECTED".equals(server.getStatus())) {
            return createErrorMessage(request, "Server is not connected: " + server.getName());
        }

        // Create and store user message
        ChatMessage userMessage = ChatMessage.builder()
                .id(UUID.randomUUID().toString())
                .role("USER")
                .content(request.getMessage())
                .timestamp(System.currentTimeMillis())
                .serverId(request.getServerId())
                .build();

        addMessageToConversation(conversationId, userMessage);

        try {
            // Use the MCP tool orchestrator to get context from the server
            String mcpContext = mcpToolOrchestrator.executeTool(server, request.getMessage());
            
            // Try to use the enhanced LLM service to generate response with conversation memory
            String assistantContent = enhancedLlmService.generateWithMemory(conversationId, request.getMessage(), mcpContext);
            
            // Check if LLM service returned an error and fall back to MCP context
            if (assistantContent != null && assistantContent.startsWith("Error generating response:")) {
                log.warn("LLM service returned error for conversation {}, falling back to MCP context: {}", 
                        conversationId, assistantContent);
                assistantContent = formatMcpResponse(mcpContext, request.getMessage(), server.getName());
            } else {
                log.info("Successfully generated LLM response for conversation {}", conversationId);
            }
            
            // Create assistant response message
            ChatMessage assistantMessage = ChatMessage.builder()
                    .id(UUID.randomUUID().toString())
                    .role("ASSISTANT")
                    .content(assistantContent)
                    .timestamp(System.currentTimeMillis())
                    .serverId(request.getServerId())
                    .build();

            addMessageToConversation(conversationId, assistantMessage);
            
            log.info("Successfully processed message using simplified implementation for conversation {} using server {}", 
                    conversationId, server.getName());
            
            return assistantMessage;

        } catch (Exception e) {
            log.error("Error processing message in simplified implementation for conversation {}: {}", conversationId, e.getMessage(), e);
            return createErrorMessage(request, "Error processing message: " + e.getMessage());
        }
    }

    private ChatMessage createErrorMessage(McpRequest request, String errorMessage) {
        return ChatMessage.builder()
                .id(UUID.randomUUID().toString())
                .role("ASSISTANT")
                .content(errorMessage)
                .timestamp(System.currentTimeMillis())
                .serverId(request.getServerId())
                .build();
    }

    private String formatMcpResponse(String mcpContext, String userMessage, String serverName) {
        if (mcpContext == null || mcpContext.trim().isEmpty()) {
            return String.format("✅ Successfully contacted %s, but no specific data was returned for: \"%s\"", 
                    serverName, userMessage);
        }
        
        try {
            // Try to parse and format JSON response
            JsonNode jsonNode = objectMapper.readTree(mcpContext);
            if (jsonNode.isObject()) {
                StringBuilder formattedResponse = new StringBuilder();
                formattedResponse.append(String.format("✅ Response from %s:\n\n", serverName));
                
                if (jsonNode.has("movies")) {
                    JsonNode movies = jsonNode.get("movies");
                    formattedResponse.append("🎬 Movies found:\n");
                    for (JsonNode movie : movies) {
                        formattedResponse.append(String.format("• %s (%s) - %s\n", 
                                movie.path("title").asText("Unknown Title"),
                                movie.path("year").asText("Unknown Year"),
                                movie.path("genre").asText("Unknown Genre")));
                    }
                } else if (jsonNode.has("movie")) {
                    JsonNode movie = jsonNode.get("movie");
                    formattedResponse.append("🎬 Movie Details:\n");
                    formattedResponse.append(String.format("Title: %s\n", movie.path("title").asText("Unknown")));
                    formattedResponse.append(String.format("Year: %s\n", movie.path("year").asText("Unknown")));
                    formattedResponse.append(String.format("Genre: %s\n", movie.path("genre").asText("Unknown")));
                    formattedResponse.append(String.format("Director: %s\n", movie.path("director").asText("Unknown")));
                    formattedResponse.append(String.format("Description: %s\n", movie.path("description").asText("No description available")));
                } else {
                    // Generic JSON formatting
                    formattedResponse.append("📋 Raw response:\n");
                    formattedResponse.append(objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(jsonNode));
                }
                
                formattedResponse.append(String.format("\n\n💡 Note: This is direct output from %s. LLM service is currently unavailable for enhanced responses.", serverName));
                return formattedResponse.toString();
            }
        } catch (Exception e) {
            log.debug("Could not parse MCP response as JSON: {}", e.getMessage());
        }
        
        // Return raw response with formatting
        return String.format("✅ Response from %s:\n\n%s\n\n💡 Note: LLM service is currently unavailable for enhanced responses.", 
                serverName, mcpContext);
    }

    private ChatMessage sendMessageOriginal(McpRequest request) {
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

        String protocol = extractProtocol(server.getUrl());
        String assistantContent;

        if ("stdio".equalsIgnoreCase(protocol)) {
            assistantContent = sendMessageViaStdio(server, request.getMessage());
        } else if ("http".equalsIgnoreCase(protocol) || "https".equalsIgnoreCase(protocol)) {
            assistantContent = sendMessageViaHttp(server, request.getMessage());
        } else {
            // Fallback al LLM local
            ChatMessage contextMessage = ChatMessage.builder()
                    .id(UUID.randomUUID().toString())
                    .role("SYSTEM")
                    .content("Contexto no implementado todavía")
                    .timestamp(System.currentTimeMillis())
                    .serverId(request.getServerId())
                    .build();
            assistantContent = llmService.generate(request.getMessage(), contextMessage.getContent());
        }

        ChatMessage assistantMessage = ChatMessage.builder()
                .id(UUID.randomUUID().toString())
                .role("ASSISTANT")
                .content(assistantContent)
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

    private String extractProtocol(String url) {
        if (url == null || !url.contains("://")) {
            return "unknown";
        }
        return url.substring(0, url.indexOf("://"));
    }

    private String sendMessageViaStdio(McpServer server, String message) {
        try {
            Process process = mcpServerService.getStdioProcess(server.getId());
            OutputStream stdin = mcpServerService.getStdioInput(server.getId());
            InputStream stdout = mcpServerService.getStdioOutput(server.getId());

            if (process == null || stdin == null || stdout == null) {
                log.error("STDIO process/streams not found for server {}", server.getId());
                return "Error: STDIO process not available";
            }

            // Determinar la herramienta apropiada basada en el mensaje
            String toolName = mcpToolService.selectBestTool(message, server);
            Map<String, Object> toolArgs = mcpToolService.extractToolArguments(message, toolName);

            // Usar tools/call con la herramienta seleccionada
            String response = mcpToolService.callToolViaStdio(stdin, stdout, toolName, toolArgs);

            log.info("Respuesta stdio de {}: {}", server.getName(), response);
            if (response == null) {
                return "Error: No response from MCP server";
            }
            try {
                JsonNode root = objectMapper.readTree(response);
                if (root.has("result")) {
                    return root.get("result").toString();
                } else if (root.has("error")) {
                    return "Error MCP: " + root.get("error").toString();
                } else {
                    return "Respuesta inesperada del MCP server: " + response;
                }
            } catch (Exception parseException) {
                log.warn("Could not parse MCP response as JSON: {}", response);
                return response; // Return raw response if not JSON
            }
        } catch (Exception e) {
            log.error("Error comunicando por stdio: {}", e.getMessage(), e);
            return "Error comunicando con el MCP server por stdio: " + e.getMessage();
        }
    }

    private String sendMessageViaHttp(McpServer server, String message) {
        try {
            // Obtener herramientas disponibles dinámicamente
            List<Map<String, Object>> availableTools = mcpToolService.getTools(server);
            log.debug("Herramientas disponibles en {}: {}", server.getName(), availableTools);

            // Seleccionar la mejor herramienta
            String selectedTool = mcpToolService.selectBestTool(message, server);
            log.debug("Herramienta seleccionada para '{}': {}", message, selectedTool);

            // Extraer argumentos para la herramienta
            Map<String, Object> toolArgs = mcpToolService.extractToolArguments(message, selectedTool);
            log.debug("Argumentos extraídos: {}", toolArgs);

            // Usar tools/call con la herramienta seleccionada
            String response = mcpToolService.callToolViaHttp(server, selectedTool, toolArgs);

            JsonNode root = objectMapper.readTree(response);
            if (root.has("result")) {
                return root.get("result").toString();
            } else if (root.has("error")) {
                return "Error MCP: " + root.get("error").toString();
            } else {
                return "Respuesta inesperada del MCP server: " + response;
            }
        } catch (Exception e) {
            log.error("Error comunicando por HTTP: {}", e.getMessage(), e);
            return "Error comunicando con el MCP server por HTTP: " + e.getMessage();
        }
    }
}