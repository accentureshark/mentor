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

        // Validar y obtener el servidor
        Optional<McpServer> serverOpt = mcpServerService.getServer(request.getServerId());
        if (serverOpt.isEmpty()) {
            return createErrorMessage(request, "Servidor no encontrado: " + request.getServerId());
        }
        McpServer server = serverOpt.get();
        if (!"CONNECTED".equals(server.getStatus())) {
            String errorDetails = server.getLastError() != null ? " (Error: " + server.getLastError() + ")" : "";
            String errorMessage = "El servidor no está conectado: " + server.getName() + errorDetails + ". Use el botón de conexión en la lista de servidores para intentar conectar.";
            return createErrorMessage(request, errorMessage);
        }

        // Detectar conexión inicial (mensaje vacío) y retornar herramientas disponibles
        String query = request.getMessage();
        if (query == null || query.trim().isEmpty()) {
            return createInitialConnectionMessage(request, server);
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
                log.warn("El servicio LLM devolvió error para la conversación {}, usando contexto MCP: {}", conversationId, assistantContent);
                assistantContent = formatMcpResponse(context, request.getMessage(), server.getName());
            } else {
                log.info("Respuesta LLM generada exitosamente para la conversación {}", conversationId);
            }

            ChatMessage assistantMessage = ChatMessage.builder()
                    .id(UUID.randomUUID().toString())
                    .role("ASSISTANT")
                    .content(assistantContent)
                    .timestamp(System.currentTimeMillis())
                    .serverId(request.getServerId())
                    .build();

            addMessageToConversation(conversationId, assistantMessage);

            log.info("Mensaje procesado exitosamente para conversación {} usando servidor {}", conversationId, server.getName());

            return assistantMessage;

        } catch (Exception e) {
            log.error("Error procesando mensaje en implementación simplificada para conversación {}: {}", conversationId, e.getMessage(), e);
            return createErrorMessage(request, "Error procesando mensaje: " + e.getMessage());
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

        // Obtener herramientas disponibles
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
        
        messageContent.append("¿Qué pregunta tienes?");
        
        ChatMessage initialMessage = ChatMessage.builder()
                .id(UUID.randomUUID().toString())
                .role("ASSISTANT")
                .content(messageContent.toString())
                .timestamp(System.currentTimeMillis())
                .serverId(request.getServerId())
                .build();
        
        // Agregar mensaje a la conversación
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
            return String.format("✅ Se contactó exitosamente con %s, pero no se devolvieron datos específicos para: \"%s\"", 
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
        StringBuilder formattedResponse = new StringBuilder();
        formattedResponse.append(String.format("✅ **Respuesta de %s**\n\n", serverName));
        

         formattedResponse.append(formatGenericStructuredResponse(jsonNode));

        
        formattedResponse.append(String.format("\n\n💡 *Información proporcionada por %s*", serverName));
        return formattedResponse.toString();
    }





    private String formatFileResponse(JsonNode jsonNode) {
        StringBuilder response = new StringBuilder();
        response.append("📁 **Archivos encontrados:**\n\n");
        
        JsonNode files = jsonNode.has("files") ? jsonNode.get("files") : 
                        jsonNode.has("result") ? jsonNode.get("result").get("files") : jsonNode;
        
        if (files.isArray()) {
            for (JsonNode file : files) {
                response.append(String.format("📄 **%s**\n", file.path("name").asText("archivo")));
                if (file.has("size")) {
                    response.append(String.format("📏 Tamaño: %s\n", file.get("size").asText()));
                }
                if (file.has("modified") || file.has("lastModified")) {
                    String modified = file.has("modified") ? file.get("modified").asText() :
                                     file.get("lastModified").asText();
                    response.append(String.format("📅 Modificado: %s\n", modified));
                }
                response.append("\n");
            }
        }
        
        return response.toString();
    }



    private String formatGenericStructuredResponse(JsonNode jsonNode) {
        StringBuilder response = new StringBuilder();
        response.append("📋 **Información estructurada:**\n\n");
        
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
        response.append(String.format("✅ **Respuesta de %s**\n\n", serverName));
        
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
        
        response.append(String.format("\n\n💡 *Información proporcionada por %s*", serverName));
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

        // Detectar conexión inicial (mensaje vacío) y retornar herramientas disponibles
        String query = request.getMessage();
        if (query == null || query.trim().isEmpty()) {
            return createInitialConnectionMessage(request, server);
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