package org.shark.mentor.mcp.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.shark.mentor.mcp.model.ChatMessage;
import org.shark.mentor.mcp.model.McpRequest;
import org.shark.mentor.mcp.model.McpServer;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.http.HttpClient;
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

        // Validar y obtener el servidor
        Optional<McpServer> serverOpt = mcpServerService.getServer(request.getServerId());
        if (serverOpt.isEmpty()) {
            return createErrorMessage(request, "Servidor no encontrado: " + request.getServerId());
        }
        McpServer server = serverOpt.get();
        if (!"CONNECTED".equals(server.getStatus())) {
            String errorDetails = server.getLastError() != null ? " (Error: " + server.getLastError() + ")" : "";
            String errorMessage = "The server is not connected: " + server.getName() + errorDetails + ". Use the connection button in the server list to attempt to connect.";
            return createErrorMessage(request, errorMessage);
        }

        // Detect initial connection (empty message) - don't create initial message
        String query = request.getMessage();
        if (query == null || query.trim().isEmpty()) {
            // For initial connections, don't create any message
            // Just return null so the frontend knows there's no initial message
            return null;
        }
        
        // Handle special commands that should not be processed as tool calls
        if ("enable_toolset".equals(query.trim())) {
            // This is a special command for dynamic toolsets - acknowledge it but don't process as tool call
            ChatMessage acknowledgeMessage = ChatMessage.builder()
                    .id(UUID.randomUUID().toString())
                    .role("ASSISTANT")
                    .content("✅ Dynamic toolsets enabled for " + server.getName())
                    .timestamp(System.currentTimeMillis())
                    .serverId(request.getServerId())
                    .build();
            addMessageToConversation(conversationId, acknowledgeMessage);
            log.info("Acknowledged dynamic toolset enablement for conversation {}", conversationId);
            return acknowledgeMessage;
        }

        // Crear y guardar el mensaje del usuario
        ChatMessage userMessage = ChatMessage.builder()
                .id(UUID.randomUUID().toString())
                .role("USER")
                .content(request.getMessage())
                .timestamp(System.currentTimeMillis())
                .serverId(request.getServerId())
                .build();
        addMessageToConversation(conversationId, userMessage);

        try {
            String context = mcpToolOrchestrator.executeTool(server, query);

            if (context == null || context.trim().isEmpty()) {
                context = "No se encontraron resultados relevantes para la consulta.";
            }

            String assistantContent = enhancedLlmService.generateWithMemory(conversationId, query, context);

            if (assistantContent != null && assistantContent.startsWith("Error generating response:")) {
                log.warn("LLM service returned error for conversation {}, using MCP context: {}", conversationId, assistantContent);
                assistantContent = formatMcpResponse(context, request.getMessage(), server.getName());
            } else {
                log.info("LLM response successfully generated for conversation {}", conversationId);
            }

            ChatMessage assistantMessage = ChatMessage.builder()
                    .id(UUID.randomUUID().toString())
                    .role("ASSISTANT")
                    .content(assistantContent)
                    .timestamp(System.currentTimeMillis())
                    .serverId(request.getServerId())
                    .build();

            addMessageToConversation(conversationId, assistantMessage);

            log.info("Message processed successfully for conversation {} using server {}", conversationId, server.getName());

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

    private ChatMessage createInitialConnectionMessage(McpRequest request, McpServer server) {
        StringBuilder messageContent = new StringBuilder();

        // Retrieve available tools
        List<Map<String, Object>> tools = mcpToolService.getTools(server);
        
//        if (!tools.isEmpty()) {
//            messageContent.append("🛠️ **Herramientas disponibles en ").append(server.getName()).append(":**\n\n");
//            for (Map<String, Object> tool : tools) {
//                Object nameObj = tool.get("name");
//                Object descObj = tool.get("description");
//                if (nameObj != null) {
//                    messageContent.append("• **").append(nameObj.toString()).append("**");
//                    if (descObj != null) {
//                        messageContent.append(": ").append(descObj.toString());
//                    }
//                    messageContent.append("\n");
//                }
//            }
//            messageContent.append("\n");
//        }
        
        messageContent.append("What is your question?");
        
        ChatMessage initialMessage = ChatMessage.builder()
                .id(UUID.randomUUID().toString())
                .role("ASSISTANT")
                .content(messageContent.toString())
                .timestamp(System.currentTimeMillis())
                .serverId(request.getServerId())
                .build();
        
        // Add message to the conversation
        String conversationId = request.getConversationId();
        if (conversationId == null) {
            conversationId = "default";
        }
        addMessageToConversation(conversationId, initialMessage);
        
        log.info("Created initial connection message for server {} with {} tools", server.getName(), tools.size());
        
        return initialMessage;
    }

    private String formatMcpResponse(String mcpContext, String userMessage, String serverName) {
        if (mcpContext == null || mcpContext.trim().isEmpty()) {
            return String.format("✅ Successfully contacted %s, but no specific data was returned for: \"%s\"",
                    serverName, userMessage);
        }
        
        try {
            // Try to parse and format JSON response
            JsonNode jsonNode = objectMapper.readTree(mcpContext);
            if (jsonNode.isObject() || jsonNode.isArray()) {
                return formatStructuredMcpResponse(jsonNode, serverName, userMessage);
            }
        } catch (Exception e) {
            log.debug("No se pudo analizar la respuesta MCP como JSON: {}", e.getMessage());
        }
        
        // Return raw response with improved formatting
        return formatRawMcpResponse(mcpContext, serverName);
    }

    private String formatStructuredMcpResponse(JsonNode jsonNode, String serverName, String userMessage) {
        String safeServerName = (serverName != null && !serverName.isBlank()) ? serverName : "Unknown MCP Server";
        StringBuilder formattedResponse = new StringBuilder();
        formattedResponse.append(String.format("✅ **Response from %s**\n\n", safeServerName));
        formattedResponse.append(formatGenericStructuredResponse(jsonNode));
        formattedResponse.append(String.format("\n\n💡 \\*Information provided by %s\\*", safeServerName));
        return formattedResponse.toString();
    }


    private String formatFileResponse(JsonNode jsonNode) {
        StringBuilder response = new StringBuilder();
        response.append("📁 **Archivos encontrados:**\n\n");
        
        JsonNode files = jsonNode.has("files") ? jsonNode.get("files") : 
                        jsonNode.has("result") ? jsonNode.get("result").get("files") : jsonNode;
        
        if (files.isArray()) {
            for (JsonNode file : files) {
                response.append(String.format("📄 **%s**\n", file.path("name").asText("file")));
                if (file.has("size")) {
                    response.append(String.format("📏 Size: %s\n", file.get("size").asText()));
                }
                if (file.has("modified") || file.has("lastModified")) {
                    String modified = file.has("modified") ? file.get("modified").asText() :
                                     file.get("lastModified").asText();
                    response.append(String.format("📅 Modified: %s\n", modified));
                }
                response.append("\n");
            }
        }
        
        return response.toString();
    }



    private String formatGenericStructuredResponse(JsonNode jsonNode) {
        StringBuilder response = new StringBuilder();
        response.append("📋 **Structured information:**\n\n");
        
        try {
            String prettyJson = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(jsonNode);
            response.append("```json\n").append(prettyJson).append("\n```\n");
        } catch (Exception e) {
            response.append(jsonNode.toString());
        }
        
        return response.toString();
    }

    private String formatRawMcpResponse(String mcpContext, String serverName) {
        StringBuilder response = new StringBuilder();
        response.append(String.format("✅ **Response from %s**\n\n", serverName));
        
        // Try to detect if it's a list or structured text
        if (mcpContext.contains("* ") || mcpContext.contains("- ")) {
            // Already contains list formatting, improve it
            String[] lines = mcpContext.split("\n");
            for (String line : lines) {
                line = line.trim();
                if (line.startsWith("* ") || line.startsWith("- ")) {
                    response.append("🔹 ").append(line.substring(2)).append("\n");
                } else if (!line.isEmpty()) {
                    response.append(line).append("\n");
                }
            }
        } else {
            // Plain text, add some structure
            response.append("📝 ").append(mcpContext);
        }
        
        response.append(String.format("\n\n💡 *Information provided by %s*", serverName));
        return response.toString();
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
            String errorDetails = server.getLastError() != null ? " (Error: " + server.getLastError() + ")" : "";
            throw new IllegalStateException("Server is not connected: " + server.getName() + errorDetails + ". Use the connection button in the server list to connect.");
        }

        // Detect initial connection (empty message) - don't create initial message
        String query = request.getMessage();
        if (query == null || query.trim().isEmpty()) {
            // For initial connections, don't create any message
            // Just return null so the frontend knows there's no initial message
            return null;
        }
        
        // Handle special commands that should not be processed as tool calls
        if ("enable_toolset".equals(query.trim())) {
            // This is a special command for dynamic toolsets - acknowledge it but don't process as tool call
            ChatMessage acknowledgeMessage = ChatMessage.builder()
                    .id(UUID.randomUUID().toString())
                    .role("ASSISTANT")
                    .content("✅ Dynamic toolsets enabled for " + server.getName())
                    .timestamp(System.currentTimeMillis())
                    .serverId(request.getServerId())
                    .build();
            addMessageToConversation(conversationId, acknowledgeMessage);
            log.info("Acknowledged dynamic toolset enablement for conversation {}", conversationId);
            return acknowledgeMessage;
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
            // Fallback to local LLM
            ChatMessage contextMessage = ChatMessage.builder()
                    .id(UUID.randomUUID().toString())
                    .role("SYSTEM")
                    .content("Context not implemented yet")
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

            // Determine the appropriate tool based on the message
            List<Map<String, Object>> availableTools = mcpToolService.getTools(server);
            String toolName = mcpToolService.selectBestTool(message, server);
            Map<String, Object> toolSchema = availableTools.stream()
                    .filter(t -> toolName.equals(t.get("name")))
                    .findFirst()
                    .orElse(null);
            Map<String, Object> toolArgs = mcpToolService.extractToolArguments(message, toolName,
                    toolSchema != null ? (Map<String, Object>) toolSchema.get("inputSchema") : null);

            // Use tools/call with the selected tool
            String response = mcpToolService.callToolViaStdio(server, stdin, stdout, toolName, toolArgs);

            log.info("Stdio response from {}: {}", server.getName(), response);
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
                    return "Unexpected response from MCP server: " + response;
                }
            } catch (Exception parseException) {
                log.warn("Could not parse MCP response as JSON: {}", response);
                return response; // Return raw response if not JSON
            }
        } catch (Exception e) {
            log.error("Error communicating via stdio: {}", e.getMessage(), e);
            return "Error communicating with the MCP server via stdio: " + e.getMessage();
        }
    }

    private String sendMessageViaHttp(McpServer server, String message) {
        try {
            // Retrieve available tools dynamically
            List<Map<String, Object>> availableTools = mcpToolService.getTools(server);
            log.debug("Available tools on {}: {}", server.getName(), availableTools);

            // Select the best tool
            String selectedTool = mcpToolService.selectBestTool(message, server);
            log.debug("Selected tool for '{}': {}", message, selectedTool);

            // Extract arguments for the tool
            Map<String, Object> toolSchema = availableTools.stream()
                    .filter(t -> selectedTool.equals(t.get("name")))
                    .findFirst()
                    .orElse(null);
            Map<String, Object> toolArgs = mcpToolService.extractToolArguments(message, selectedTool,
                    toolSchema != null ? (Map<String, Object>) toolSchema.get("inputSchema") : null);
            log.debug("Extracted arguments: {}", toolArgs);

            // Use tools/call with the selected tool
            String response = mcpToolService.callToolViaHttp(server, selectedTool, toolArgs);

            JsonNode root = objectMapper.readTree(response);
            if (root.has("result")) {
                return root.get("result").toString();
            } else if (root.has("error")) {
                return "Error MCP: " + root.get("error").toString();
            } else {
                return "Unexpected response from MCP server: " + response;
            }
        } catch (Exception e) {
            log.error("Error communicating via HTTP: {}", e.getMessage(), e);
            return "Error communicating with the MCP server via HTTP: " + e.getMessage();
        }
    }
}