package org.shark.mentor.mcp.service;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatLanguageModel;
import lombok.extern.slf4j.Slf4j;
import org.shark.mentor.mcp.config.LlmProperties;
import org.shark.mentor.mcp.config.UiProperties;
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
@Primary
public class LlmServiceEnhanced implements LlmService {

    private final LlmProperties props;
    private final I18nService i18nService;
    private ChatLanguageModel chatModel;
    private final Map<String, ChatMemory> conversationMemories = new ConcurrentHashMap<>();
    
    // Primary constructor
    public LlmServiceEnhanced(LlmProperties props, I18nService i18nService) {
        this.props = props;
        this.i18nService = i18nService;
    }
    
    // Backward compatibility constructor for tests
    public LlmServiceEnhanced(LlmProperties props) {
        this.props = props;
        UiProperties uiProps = new UiProperties();
        uiProps.setLocale("en");
        this.i18nService = new I18nService(uiProps);
    }

    @jakarta.annotation.PostConstruct
    public void initModel() {
        log.info("Initializing enhanced LLM model with provider: {}", props.getProvider());
        chatModel = LlmFactory.createChatModel(
                props.getProvider(),
                props.getModel(),
                props.getApi().getBaseUrl(),
                props.getApi().getKey(),
                props.getModelConfig().getTemperature(),
                props.getModelConfig().getTimeoutMinutes()
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
        prompt.append(i18nService.getMessage("context.mcp")).append(":\n");
        prompt.append(context);
        prompt.append("\n\n").append(i18nService.getMessage("instructions.formatting")).append(":\n");

        // Determine the type of response based on context content
        if (context.toLowerCase().contains("table") || context.toLowerCase().contains("schema") || context.toLowerCase().contains("column")) {
            prompt.append("This appears to be database/table related information:\n");
            prompt.append("- Use ").append(i18nService.getMessage("prefix.file")).append(" for table names and ").append(i18nService.getMessage("prefix.structure")).append(" for structure information\n");
            prompt.append("- List columns with their types and descriptions clearly\n");
            prompt.append("- Include row counts and size information if available\n");
            prompt.append("- Format as structured lists for easy reading\n");
        } else if (context.toLowerCase().contains("query") || context.toLowerCase().contains("select") || context.toLowerCase().contains("data")) {
            prompt.append("This appears to be query result information:\n");
            prompt.append("- Use ").append(i18nService.getMessage("prefix.data")).append(" for query results and ").append(i18nService.getMessage("prefix.chart")).append(" for data summaries\n");
            prompt.append("- Highlight key findings and patterns in the data\n");
            prompt.append("- Include record counts and aggregation results\n");
            prompt.append("- Present data in tabular format when appropriate\n");
        } else if (context.toLowerCase().contains("repository") || context.toLowerCase().contains("github") || context.toLowerCase().contains("code")) {
            prompt.append("This appears to be code repository information:\n");
            prompt.append("- Use ").append(i18nService.getMessage("prefix.code")).append(" for repositories and ").append(i18nService.getMessage("prefix.tool")).append(" for functions/tools\n");
            prompt.append("- Include repository details, file structures, or code snippets\n");
            prompt.append("- Show status information and any execution results\n");
        } else {
            prompt.append("Organize the information clearly with:\n");
            prompt.append("- Descriptive titles with appropriate emojis\n");
            prompt.append("- Information structured in lists\n");
            prompt.append("- Use of markdown for formatting\n");
            prompt.append("- Clear separation between elements\n");
        }

        prompt.append("\nAlways end with: ").append(i18nService.getMessage("info.provided.by", "el servidor MCP"));
        return prompt.toString();
    }

    /**
     * Build MCP-compliant system prompt that ensures localized responses and focuses on tool understanding
     */
    private String buildSystemPrompt() {
        String currentLocale = i18nService.getCurrentLocale().toString();
        
        return "You are a helpful assistant that works with MCP (Model Context Protocol) servers.\n" +
            "You specialize in understanding and executing tool requests across different domains like data lakes, GitHub, files, APIs, and more.\n\n" +
            "Important guidelines:\n" +
            "1. Respond ONLY using information provided in the context of MCP servers\n" +
            "2. Do not infer or add information that is not explicitly indicated in the context\n" +
            "3. If the context is insufficient to answer the question, clearly state what information is missing\n" +
            "4. Be precise and factual in your responses\n" +
            "5. When relevant, mention which MCP server provided the information\n" +
            "6. Respond in the user's preferred language (current locale: " + currentLocale + ")\n" +
            "7. Focus on helping users understand the capabilities and results of MCP tools\n\n" +
            "RESPONSE FORMAT:\n" +
            "- Use clear titles and subtitles with appropriate emojis\n" +
            "- For data queries: " + i18nService.getMessage("prefix.data") + " title, " + i18nService.getMessage("prefix.chart") + " results, summary\n" +
            "- For tables/schemas: " + i18nService.getMessage("prefix.file") + " name, " + i18nService.getMessage("prefix.structure") + " structure, size\n" +
            "- For files: file name, size, date\n" +
            "- For code/GitHub: " + i18nService.getMessage("prefix.code") + " repository, " + i18nService.getMessage("prefix.tool") + " function, status\n" +
            "- For APIs/tools: tool name, purpose, results\n" +
            "- Organize information in numbered or bulleted lists\n" +
            "- Use proper spacing between sections\n" +
            "- If there are multiple results, list them clearly\n\n" +
            "Always maintain accuracy and transparency about the limitations of the available context.\n" +
            "All responses must be well formatted and in the user's preferred language.\n" +
            "Adapt the format based on the type of MCP server and tool being used.";
    }
}