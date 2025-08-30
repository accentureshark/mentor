package org.shark.mentor.mcp.service;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatLanguageModel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.shark.mentor.mcp.config.LlmProperties;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

/**
 * Enhanced LLM service using langchain4j best practices for conversation management
 * and MCP-compliant response generation
 */
@Slf4j
@Service("llmServiceEnhanced")
@RequiredArgsConstructor
@Primary
public class LlmServiceEnhanced implements LlmService {

    private final LlmProperties props;
    private ChatLanguageModel chatModel;
    private final Map<String, ChatMemory> conversationMemories = new ConcurrentHashMap<>();

    @jakarta.annotation.PostConstruct
    public void initModel() {
        log.info("Initializing enhanced LLM model with provider: {}", props.getProvider());
        chatModel = LlmFactory.createChatModel(
                props.getProvider(),
                props.getModel(),
                props.getApi().getBaseUrl(),
                props.getApi().getKey()
        );
        log.info("Enhanced LLM model initialized successfully");
    }

    @Override
    public String generate(String question, String context) {
        return generateWithMemory("default", question, context);
    }

    /**
     * Generate response with conversation memory support
     */
    public String generateWithMemory(String conversationId, String question, String context) {
        try {
            List<ChatMessage> messages = buildMessages(question, context);
            
            // Use langchain4j to generate response with proper context management
            String response = chatModel.generate(messages).content().text();
            
            log.debug("Generated response for conversation {}: {}", conversationId, response);
            return response;
            
        } catch (Exception e) {
            log.error("Error generating LLM response for conversation {}: {}", conversationId, e.getMessage(), e);
            return "Error generating response: " + e.getMessage();
        }
    }

    /**
     * Get or create conversation memory for a specific conversation
     */
    private ChatMemory getConversationMemory(String conversationId) {
        return conversationMemories.computeIfAbsent(conversationId, 
            k -> MessageWindowChatMemory.withMaxMessages(20));
    }

    /**
     * Clear conversation memory for a specific conversation
     */
    public void clearConversation(String conversationId) {
        conversationMemories.remove(conversationId);
        log.info("Cleared conversation memory for: {}", conversationId);
    }

    /**
     * Build proper message list for langchain4j processing
     */
    private List<ChatMessage> buildMessages(String question, String context) {
        List<ChatMessage> messages = new ArrayList<>();
        
        // System message defining MCP-compliant behavior
        String systemPrompt = buildSystemPrompt();
        messages.add(SystemMessage.from(systemPrompt));
        
        // Add context as system information if available
        if (context != null && !context.isBlank()) {
            String contextPrompt = buildContextPrompt(context, question);
            messages.add(SystemMessage.from(contextPrompt));
        }
        
        // User question
        messages.add(UserMessage.from(question));
        
        return messages;
    }

    /**
     * Build context prompt that instructs the LLM how to format the response based on MCP tool results
     */
    private String buildContextPrompt(String context, String question) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("MCP SERVER CONTEXT:\n");
        prompt.append(context);
        prompt.append("\n\nSPECIFIC FORMATTING INSTRUCTIONS:\n");

        // Determine the type of response based on context content
        if (context.toLowerCase().contains("table") || context.toLowerCase().contains("schema") || context.toLowerCase().contains("column")) {
            prompt.append("""
                This appears to be database/table related information:
                - Use 📁 for table names and 🏗️ for structure information
                - List columns with their types and descriptions clearly
                - Include row counts and size information if available
                - Format as structured lists for easy reading
                """);
        } else if (context.toLowerCase().contains("query") || context.toLowerCase().contains("select") || context.toLowerCase().contains("data")) {
            prompt.append("""
                This appears to be query result information:
                - Use 📊 for query results and 📈 for data summaries
                - Highlight key findings and patterns in the data
                - Include record counts and aggregation results
                - Present data in tabular format when appropriate
                """);
        } else if (context.toLowerCase().contains("repository") || context.toLowerCase().contains("github") || context.toLowerCase().contains("code")) {
            prompt.append("""
                This appears to be code repository information:
                - Use 💻 for repositories and 🔧 for functions/tools
                - Include repository details, file structures, or code snippets
                - Show status information and any execution results
                """);
        } else {
            prompt.append("""
                Organize the information clearly with:
                - Descriptive titles with appropriate emojis
                - Information structured in lists
                - Use of markdown for formatting
                - Clear separation between elements
                """);
        }

        prompt.append("\nAlways end with: 💡 *Información proporcionada por el servidor MCP*");
        return prompt.toString();
    }

    /**
     * Build MCP-compliant system prompt that ensures Spanish responses and focuses on tool understanding
     */
    private String buildSystemPrompt() {
        return """
            You are a helpful assistant that works with MCP (Model Context Protocol) servers.
            You specialize in understanding and executing tool requests across different domains like data lakes, GitHub, files, APIs, and more.

            Important guidelines:
            1. Respond ONLY using information provided in the context of MCP servers
            2. Do not infer or add information that is not explicitly indicated in the context
            3. If the context is insufficient to answer the question, clearly state what information is missing
            4. Be precise and factual in your responses
            5. When relevant, mention which MCP server provided the information
            6. ALWAYS respond in Spanish, regardless of the language of the question
            7. Focus on helping users understand the capabilities and results of MCP tools

            RESPONSE FORMAT:
            - Use clear titles and subtitles with appropriate emojis
            - For data queries: 📊 title, 📈 results, 📋 summary
            - For tables/schemas: 📁 name, 🏗️ structure, 📏 size
            - For files: 📄 name, 📏 size, 📅 date
            - For code/GitHub: 💻 repository, 🔧 function, 📊 status
            - For APIs/tools: ⚙️ tool name, 🎯 purpose, 📝 results
            - Organize information in numbered or bulleted lists
            - Use proper spacing between sections
            - If there are multiple results, list them clearly

            Examples for different MCP server types:
            
            📊 **Data Lake Query Results**
            🎯 **Consulta:** [user query]
            📈 **Resultados encontrados:** X registros
            📋 **Resumen:**
            - [Key findings]
            
            📁 **Tabla: [table_name]**
            🏗️ **Estructura:**
            - Campo 1: [type] - [description]
            - Campo 2: [type] - [description]
            📏 **Registros:** X filas
            
            💻 **Repositorio: [repo_name]**
            🔧 **Función ejecutada:** [tool_name]
            📊 **Estado:** [status]
            📝 **Resultado:** [description]

            Always maintain accuracy and transparency about the limitations of the available context.
            All responses must be in Spanish and well formatted.
            Adapt the format based on the type of MCP server and tool being used.
            """;
    }
}