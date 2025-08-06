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
     * Build context prompt that instructs the LLM how to format the response
     */
    private String buildContextPrompt(String context, String question) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("CONTEXTO DEL SERVIDOR MCP:\n");
        prompt.append(context);
        prompt.append("\n\nINSTRUCCIONES ESPECÍFICAS DE FORMATO:\n");
        

            prompt.append("""
                Organiza la información de forma clara con:
                - Títulos descriptivos con emojis apropiados
                - Información estructurada en listas
                - Uso de markdown para dar formato
                - Separación clara entre elementos
                """);

        
        prompt.append("\nTermina siempre con: 💡 *Información proporcionada por [nombre del servidor]*");
        return prompt.toString();
    }

    /**
     * Build MCP-compliant system prompt that ensures Spanish responses
     */
    private String buildSystemPrompt() {
        return """
            Eres un asistente útil que trabaja con servidores MCP (Model Context Protocol).
            
            Pautas importantes:
            1. Responde ÚNICAMENTE usando información proporcionada en el contexto de los servidores MCP
            2. No inferir o agregar información que no esté explícitamente indicada en el contexto
            3. Si el contexto es insuficiente para responder la pregunta, indica claramente qué información falta
            4. Sé preciso y factual en tus respuestas
            5. Cuando sea relevante, menciona qué servidor MCP proporcionó la información
            6. SIEMPRE responde en español, independientemente del idioma de la pregunta
            
            FORMATO DE RESPUESTA:
            - Usa títulos y subtítulos claros con emojis apropiados
            - Para películas: 🎬 título, 📅 año, ⭐ calificación, 📝 descripción
            - Para archivos: 📁 nombre, 📏 tamaño, 📅 fecha
            - Para código/GitHub: 💻 repositorio, 🔧 función, 📊 estado
            - Organiza la información en listas numeradas o con viñetas
            - Usa espaciado adecuado entre secciones
            - Si hay múltiples resultados, enuméralos claramente
            
            Ejemplo para películas:
            🎬 **[Título de la película]** (📅 Año)
            ⭐ Calificación: X.X/10
            📝 **Sinopsis:** [Descripción]
            🎭 **Género:** [Género]
            
            Siempre mantén la precisión y transparencia sobre las limitaciones del contexto disponible.
            Todas las respuestas deben ser en español y bien formateadas.
            """;
    }
}