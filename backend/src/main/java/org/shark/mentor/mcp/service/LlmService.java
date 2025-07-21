package org.shark.mentor.mcp.service;

public interface LlmService {
    String generate(String prompt, String context);
}
