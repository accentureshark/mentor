package org.shark.mentor.mcp.application.service.llm;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.StreamingResponseHandler;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.model.output.Response;
import lombok.extern.slf4j.Slf4j;
import org.shark.mentor.mcp.infraestructure.config.LlmProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Implementation of streaming LLM service using Server-Sent Events (SSE)
 * Provides real-time streaming of LLM responses for better user experience
 */
@Slf4j
@Service
public class StreamingLlmServiceImpl implements StreamingLlmService {

    private final LlmProperties props;
    private StreamingChatLanguageModel streamingChatModel;

    @Autowired
    public StreamingLlmServiceImpl(LlmProperties props) {
        this.props = props;
    }

    @jakarta.annotation.PostConstruct
    public void initStreamingModel() {
        if (props.getPerformance().isEnableStreaming()) {
            log.info("Initializing streaming LLM model with provider: {}", props.getProvider());
            
            // Use optimized timeout for faster responses if configured
            int timeoutMinutes = props.getPerformance().getFastTimeoutSeconds() > 0 ? 
                props.getPerformance().getFastTimeoutSeconds() / 60 : 
                props.getModelConfig().getTimeoutMinutes();
                
            streamingChatModel = LlmFactory.createStreamingChatModel(
                    props.getProvider(),
                    props.getModel(),
                    props.getApi().getBaseUrl(),
                    props.getApi().getKey(),
                    props.getModelConfig().getTemperature(),
                    timeoutMinutes
            );
            log.info("Streaming LLM model initialized successfully");
        } else {
            log.info("Streaming is disabled in configuration");
        }
    }

    @Override
    public SseEmitter generateStreaming(String question, String context) {
        return generateStreamingWithMemory("default", question, context);
    }

    @Override
    public SseEmitter generateStreamingWithMemory(String conversationId, String question, String context) {
        if (!props.getPerformance().isEnableStreaming() || streamingChatModel == null) {
            log.warn("Streaming is disabled or model not initialized for conversation {}", conversationId);
            throw new IllegalStateException("Streaming is not available");
        }

        // Create SSE emitter with configured timeout
        SseEmitter emitter = new SseEmitter(props.getPerformance().getStreamingTimeoutMillis());
        
        // Set up cleanup on completion/timeout
        emitter.onCompletion(() -> log.debug("Streaming completed for conversation {}", conversationId));
        emitter.onTimeout(() -> log.warn("Streaming timeout for conversation {}", conversationId));
        emitter.onError(throwable -> log.error("Streaming error for conversation {}: {}", conversationId, throwable.getMessage()));

        // Build messages for the LLM
        List<ChatMessage> messages = buildMessages(question, context);
        
        // Create streaming response handler
        StreamingResponseHandler<AiMessage> handler = new StreamingResponseHandler<AiMessage>() {
            private final StringBuilder responseBuilder = new StringBuilder();
            
            @Override
            public void onNext(String token) {
                try {
                    responseBuilder.append(token);
                    // Send each token as SSE data
                    emitter.send(SseEmitter.event()
                            .name("token")
                            .data(token));
                    log.trace("Streamed token for conversation {}: {}", conversationId, token);
                } catch (Exception e) {
                    log.error("Error sending streaming token for conversation {}: {}", conversationId, e.getMessage());
                    emitter.completeWithError(e);
                }
            }
            
            @Override
            public void onComplete(Response<AiMessage> response) {
                try {
                    String fullResponse = response.content().text();
                    // Send completion event
                    emitter.send(SseEmitter.event()
                            .name("complete")
                            .data(fullResponse));
                    emitter.complete();
                    log.info("Streaming completed for conversation {}, total length: {}", conversationId, fullResponse.length());
                } catch (Exception e) {
                    log.error("Error completing stream for conversation {}: {}", conversationId, e.getMessage());
                    emitter.completeWithError(e);
                }
            }
            
            @Override
            public void onError(Throwable error) {
                try {
                    log.error("Streaming error for conversation {}: {}", conversationId, error.getMessage(), error);
                    emitter.send(SseEmitter.event()
                            .name("error")
                            .data("Error generating response: " + error.getMessage()));
                    emitter.completeWithError(error);
                } catch (Exception e) {
                    log.error("Error sending error event for conversation {}: {}", conversationId, e.getMessage());
                    emitter.completeWithError(e);
                }
            }
        };

        // Start streaming generation asynchronously
        CompletableFuture.runAsync(() -> {
            try {
                log.debug("Starting streaming generation for conversation {}", conversationId);
                streamingChatModel.generate(messages, handler);
            } catch (Exception e) {
                log.error("Error starting streaming generation for conversation {}: {}", conversationId, e.getMessage(), e);
                handler.onError(e);
            }
        });

        return emitter;
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
            String contextPrompt = buildContextPrompt(context);
            messages.add(SystemMessage.from(contextPrompt));
        }
        
        // User question
        messages.add(UserMessage.from(question));
        
        return messages;
    }

    /**
     * Build MCP-compliant system prompt optimized for streaming
     */
    private String buildSystemPrompt() {
        return "You are a helpful MCP assistant. " +
            "RULES: 1) Use ONLY provided context 2) No inference 3) State missing info clearly " +
            "4) Respond in a structured way 5) Mention MCP server when relevant\n" +
            "FORMAT: Clear titles with emojis, markdown lists, proper spacing. " +
            "Be accurate and transparent about context limitations.";
    }

    /**
     * Build context prompt for the LLM
     */
    private String buildContextPrompt(String context) {
        StringBuilder prompt = new StringBuilder(512);
        prompt.append("Context from MCP server:\n");
        prompt.append(context);
        prompt.append("\n\nInstructions for formatting:\n");
        prompt.append("Organize the information clearly with:\n");
        prompt.append("- Descriptive titles with appropriate emojis\n");
        prompt.append("- Information structured in lists\n");
        prompt.append("- Use of markdown for formatting\n");
        prompt.append("- Clear separation between elements\n");
        return prompt.toString();
    }

    /**
     * Check if streaming is available
     */
    public boolean isStreamingAvailable() {
        return props.getPerformance().isEnableStreaming() && streamingChatModel != null;
    }
}