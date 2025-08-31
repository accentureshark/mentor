package org.shark.mentor.mcp.application.service.llm;

import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Service interface for streaming LLM responses using Server-Sent Events (SSE)
 */
public interface StreamingLlmService {

    /**
     * Generate a streaming response to the given question using only the supplied context.
     * Returns an SseEmitter that will stream response chunks as they are generated.
     * 
     * @param question the user's original question
     * @param context  additional information retrieved from the MCP server
     * @return SseEmitter for streaming the response chunks
     */
    SseEmitter generateStreaming(String question, String context);

    /**
     * Generate a streaming response with conversation memory support
     * 
     * @param conversationId the conversation identifier for memory management
     * @param question the user's original question
     * @param context  additional information retrieved from the MCP server
     * @return SseEmitter for streaming the response chunks
     */
    SseEmitter generateStreamingWithMemory(String conversationId, String question, String context);
}