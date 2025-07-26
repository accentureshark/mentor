package org.shark.mentor.mcp.service;

public interface LlmService {

    /**
     * Generate a response to the given question using only the supplied context.
     * Implementations must not infer or add information that is not present in
     * the context.
     *
     * @param question the user's original question
     * @param context  additional information retrieved from the MCP server
     * @return the model generated answer
     */
    String generate(String question, String context);
}
