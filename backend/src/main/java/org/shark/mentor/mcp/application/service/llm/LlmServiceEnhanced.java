package org.shark.mentor.mcp.application.service.llm;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.model.output.Response;
import lombok.extern.slf4j.Slf4j;
import org.shark.mentor.mcp.application.service.cache.LlmResponseCache;
import org.shark.mentor.mcp.application.service.i18n.I18nService;
import org.shark.mentor.mcp.application.service.notification.DynamicToolInfoService;
import org.shark.mentor.mcp.application.service.tool.ToolContextCache;
import org.shark.mentor.mcp.infraestructure.config.LlmProperties;
import org.shark.mentor.mcp.domain.model.McpServer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.Map;

/**
 * Enhanced LLM service using langchain4j best practices for conversation management
 * and MCP-compliant response generation with performance optimizations and streaming support
 */
@Slf4j
@Service("llmServiceEnhanced")
@Primary
public class LlmServiceEnhanced implements LlmService {

    private final LlmProperties props;
    private final I18nService i18nService;
    private final DynamicToolInfoService dynamicToolInfoService;
    private final ToolContextCache toolContextCache;
    private final LlmResponseCache responseCache;
    private ChatLanguageModel chatModel;
    private StreamingChatLanguageModel streamingChatModel;
    private final Map<String, ConversationMemoryEntry> conversationMemories = new ConcurrentHashMap<>();
    private final ScheduledExecutorService memoryCleanupExecutor = Executors.newSingleThreadScheduledExecutor();
    
    // Primary constructor
    @Autowired
    public LlmServiceEnhanced(LlmProperties props, I18nService i18nService, 
                             DynamicToolInfoService dynamicToolInfoService,
                             ToolContextCache toolContextCache,
                             LlmResponseCache responseCache) {
        this.props = props;
        this.i18nService = i18nService;
        this.dynamicToolInfoService = dynamicToolInfoService;
        this.toolContextCache = toolContextCache;
        this.responseCache = responseCache;
        
        // Setup conversation memory cleanup if enabled
        if (props.getPerformance().isEnableConversationMemoryCleanup()) {
            memoryCleanupExecutor.scheduleAtFixedRate(
                this::cleanupExpiredConversations, 
                10, // Initial delay
                10, // Period
                TimeUnit.MINUTES
            );
            log.info("Conversation memory cleanup enabled with TTL: {} minutes", 
                    props.getPerformance().getConversationMemoryTtlMinutes());
        }
    }

    @jakarta.annotation.PostConstruct
    public void initModel() {
        log.info("Initializing enhanced LLM model with provider: {}", props.getProvider());
        
        // Use optimized timeout for faster responses if configured
        int timeoutMinutes = props.getPerformance().getFastTimeoutSeconds() > 0 ? 
            props.getPerformance().getFastTimeoutSeconds() / 60 : 
            props.getModelConfig().getTimeoutMinutes();
        
        // Initialize regular chat model
        chatModel = LlmFactory.createChatModel(
                props.getProvider(),
                props.getModel(),
                props.getApi().getBaseUrl(),
                props.getApi().getKey(),
                props.getModelConfig().getTemperature(),
                timeoutMinutes
        );
        
        // Initialize streaming chat model - this is optional
        try {
            streamingChatModel = LlmFactory.createStreamingChatModel(
                    props.getProvider(),
                    props.getModel(),
                    props.getApi().getBaseUrl(),
                    props.getApi().getKey(),
                    props.getModelConfig().getTemperature(),
                    timeoutMinutes
            );
            log.info("Enhanced LLM model initialized with streaming support, timeout: {}min", timeoutMinutes);
        } catch (Exception e) {
            log.info("Streaming model not available, using simulated streaming mode: {}", e.getMessage());
            streamingChatModel = null;
        }
    }

    @Override
    public String generate(String question, String context) {
        return generateWithMemory("default", question, context);
    }

    @Override
    public Flux<String> generateStream(String question, String context) {
        return generateStreamWithMemory("default", question, context);
    }

    /**
     * Generate streaming response with conversation memory support
     */
    public Flux<String> generateStreamWithMemory(String conversationId, String question, String context) {
        return generateStreamWithMemory(conversationId, question, context, null);
    }

    /**
     * Generate streaming response with conversation memory support and MCP server context
     */
    public Flux<String> generateStreamWithMemory(String conversationId, String question, String context, McpServer server) {
        try {
            // Check if streaming is enabled in configuration
            if (!props.getPerformance().isEnableStreaming()) {
                log.debug("Streaming disabled in configuration, falling back to synchronous response for conversation {}", conversationId);
                return Flux.just(generateWithMemory(conversationId, question, context, server));
            }
            
            // Check cache first if enabled
            if (props.getPerformance().isEnableCaching()) {
                String cachedResponse = responseCache.get(question, context);
                if (cachedResponse != null) {
                    log.debug("Returning cached response as stream for conversation {}", conversationId);
                    // Simulate streaming by splitting cached response into chunks
                    return simulateStreamingFromCache(cachedResponse);
                }
            }
            
            List<ChatMessage> messages = buildMessages(question, context, server);
            
            // For now, use a simpler approach: generate the response asynchronously and emit it in chunks
            return Mono.fromCallable(() -> {
                // Check if chatModel is available
                if (chatModel == null) {
                    throw new RuntimeException("LLM model not initialized");
                }
                
                // Use the regular model if streaming model fails to initialize
                String response = chatModel.generate(messages).content().text();
                
                // Cache the response if enabled
                if (props.getPerformance().isEnableCaching() && 
                    response != null && !response.trim().isEmpty()) {
                    responseCache.put(question, context, response);
                }
                
                log.debug("Generated async response for streaming in conversation {}", conversationId);
                return response;
            })
            .flatMapMany(this::simulateStreamingFromResponse)
            .onErrorResume(error -> {
                log.error("Error in streaming LLM response for conversation {}: {}", 
                         conversationId, error.getMessage(), error);
                return Flux.just("Error generating response: " + error.getMessage());
            });
            
        } catch (Exception e) {
            log.error("Error setting up streaming LLM response for conversation {}: {}", conversationId, e.getMessage(), e);
            return Flux.error(e);
        }
    }

    /**
     * Simulate streaming from a cached response by splitting into words
     */
    private Flux<String> simulateStreamingFromCache(String cachedResponse) {
        String[] words = cachedResponse.split("\\s+");
        int chunkSize = props.getPerformance().getStreamingWordChunkSize();
        int delayMs = props.getPerformance().getStreamingDelayMs() / 2; // Faster for cached responses
        
        return Flux.fromIterable(createWordChunks(words, chunkSize))
                .delayElements(java.time.Duration.ofMillis(delayMs));
    }

    /**
     * Simulate streaming from a complete response by splitting into words
     */
    private Flux<String> simulateStreamingFromResponse(String response) {
        String[] words = response.split("\\s+");
        int chunkSize = props.getPerformance().getStreamingWordChunkSize();
        int delayMs = props.getPerformance().getStreamingDelayMs();
        
        return Flux.fromIterable(createWordChunks(words, chunkSize))
                .delayElements(java.time.Duration.ofMillis(delayMs));
    }

    /**
     * Create chunks of words for streaming
     */
    private List<String> createWordChunks(String[] words, int chunkSize) {
        List<String> chunks = new ArrayList<>();
        StringBuilder chunk = new StringBuilder();
        
        for (int i = 0; i < words.length; i++) {
            chunk.append(words[i]).append(" ");
            
            if ((i + 1) % chunkSize == 0 || i == words.length - 1) {
                chunks.add(chunk.toString());
                chunk = new StringBuilder();
            }
        }
        
        return chunks;
    }

    /**
     * Generate response with conversation memory support
     */
    public String generateWithMemory(String conversationId, String question, String context) {
        return generateWithMemory(conversationId, question, context, null);
    }

    /**
     * Generate response with conversation memory support and MCP server context for dynamic formatting
     */
    public String generateWithMemory(String conversationId, String question, String context, McpServer server) {
        try {
            // Check cache first if enabled
            if (props.getPerformance().isEnableCaching()) {
                String cachedResponse = responseCache.get(question, context);
                if (cachedResponse != null) {
                    log.debug("Returning cached response for conversation {}", conversationId);
                    return cachedResponse;
                }
            }
            
            List<ChatMessage> messages = buildMessages(question, context, server);
            
            // Use langchain4j to generate response with proper context management
            String response = chatModel.generate(messages).content().text();
            
            // Cache the response if enabled
            if (props.getPerformance().isEnableCaching() && response != null && !response.trim().isEmpty()) {
                responseCache.put(question, context, response);
            }
            
            log.debug("Generated response for conversation {}: {}", conversationId, response);
            return response;
            
        } catch (Exception e) {
            log.error("Error generating LLM response for conversation {}: {}", conversationId, e.getMessage(), e);
            return "Error generating response: " + e.getMessage();
        }
    }

    /**
     * Get or create conversation memory for a specific conversation with TTL tracking
     */
    private ChatMemory getConversationMemory(String conversationId) {
        return conversationMemories.computeIfAbsent(conversationId, k -> {
            long expirationTime = System.currentTimeMillis() + 
                TimeUnit.MINUTES.toMillis(props.getPerformance().getConversationMemoryTtlMinutes());
            ChatMemory memory = MessageWindowChatMemory.withMaxMessages(20);
            return new ConversationMemoryEntry(memory, expirationTime);
        }).memory;
    }

    /**
     * Clear conversation memory for a specific conversation
     */
    public void clearConversation(String conversationId) {
        conversationMemories.remove(conversationId);
        log.info("Cleared conversation memory for: {}", conversationId);
    }

    /**
     * Clean up expired conversation memories to prevent memory leaks
     */
    private void cleanupExpiredConversations() {
        long currentTime = System.currentTimeMillis();
        int initialSize = conversationMemories.size();
        
        conversationMemories.entrySet().removeIf(entry -> 
            entry.getValue().expirationTime < currentTime);
        
        int finalSize = conversationMemories.size();
        
        // Also enforce maximum conversation limit
        if (finalSize > props.getPerformance().getMaxConversationsInMemory()) {
            // Remove oldest conversations
            List<String> conversationsToRemove = conversationMemories.entrySet().stream()
                .sorted(Map.Entry.<String, ConversationMemoryEntry>comparingByValue(
                    (a, b) -> Long.compare(a.expirationTime, b.expirationTime)))
                .limit(finalSize - props.getPerformance().getMaxConversationsInMemory())
                .map(Map.Entry::getKey)
                .toList();
            
            conversationsToRemove.forEach(conversationMemories::remove);
            finalSize = conversationMemories.size();
        }
        
        if (initialSize != finalSize) {
            log.debug("Conversation memory cleanup: removed {} expired/excess conversations, {} remaining", 
                     initialSize - finalSize, finalSize);
        }
    }

    /**
     * Build proper message list for langchain4j processing
     */
    private List<ChatMessage> buildMessages(String question, String context) {
        return buildMessages(question, context, null);
    }

    /**
     * Build proper message list for langchain4j processing with optional server context
     */
    private List<ChatMessage> buildMessages(String question, String context, McpServer server) {
        List<ChatMessage> messages = new ArrayList<>();
        
        // System message defining MCP-compliant behavior
        String systemPrompt = buildSystemPrompt();
        messages.add(SystemMessage.from(systemPrompt));
        
        // Add context as system information if available
        if (context != null && !context.isBlank()) {
            String contextPrompt = buildContextPrompt(context, question, server);
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
        return buildContextPrompt(context, question, null);
    }

    /**
     * Build context prompt that dynamically determines formatting based on MCP server tools
     */
    private String buildContextPrompt(String context, String question, McpServer server) {
        if (server != null) {
            // Use comprehensive context with caching to reduce LLM calls
            return toolContextCache.buildComprehensivePrompt(server, context, question);
        } else {
            // Fallback to basic context when no server is available - optimized for performance
            StringBuilder prompt = new StringBuilder(512); // Pre-allocate capacity
            prompt.append(i18nService.getMessage("context.mcp")).append(":\n");
            prompt.append(context);
            prompt.append("\n\n").append(i18nService.getMessage("instructions.formatting")).append(":\n");
            prompt.append("Organize the information clearly with:\n");
            prompt.append("- Descriptive titles with appropriate emojis\n");
            prompt.append("- Information structured in lists\n");
            prompt.append("- Use of markdown for formatting\n");
            prompt.append("- Clear separation between elements\n");
            prompt.append("\nAlways end with: ").append(i18nService.getMessage("info.provided.by", "[server name]"));
            return prompt.toString();
        }
    }

    /**
     * Build MCP-compliant system prompt that ensures localized responses and focuses on tool understanding
     * Optimized to be more concise for faster processing
     */
    private String buildSystemPrompt() {
        String currentLocale = i18nService.getCurrentLocale().toString();
        
        // Optimized shorter prompt for better performance
        return "You are a helpful MCP assistant. " +
            "RULES: 1) Use ONLY provided context 2) No inference 3) State missing info clearly " +
            "4) Respond in locale: " + currentLocale + " 5) Mention MCP server when relevant\n" +
            "FORMAT: Clear titles with emojis, markdown lists, proper spacing. " +
            "Be accurate and transparent about context limitations.";
    }
    
    /**
     * Get cache statistics for monitoring
     */
    public Map<String, Object> getCacheStats() {
        Map<String, Object> stats = new ConcurrentHashMap<>();
        stats.put("responseCacheSize", responseCache.size());
        stats.put("conversationMemoriesCount", conversationMemories.size());
        stats.put("modelCacheSize", LlmFactory.getCacheSize());
        stats.put("streamingModelCacheSize", LlmFactory.getStreamingCacheSize());
        stats.put("streamingAvailable", streamingChatModel != null);
        return stats;
    }
    
    /**
     * Clear all caches (useful for testing or configuration changes)
     */
    public void clearAllCaches() {
        if (props.getPerformance().isEnableCaching()) {
            responseCache.clear();
        }
        conversationMemories.clear();
        log.info("Cleared all LLM caches");
    }
    
    private static class ConversationMemoryEntry {
        final ChatMemory memory;
        final long expirationTime;
        
        ConversationMemoryEntry(ChatMemory memory, long expirationTime) {
            this.memory = memory;
            this.expirationTime = expirationTime;
        }
    }
}