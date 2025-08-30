package org.shark.mentor.mcp.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.shark.mentor.mcp.model.McpPrompt;
import org.shark.mentor.mcp.model.McpServer;
import org.shark.mentor.mcp.service.McpPromptService;
import org.shark.mentor.mcp.service.McpServerService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/mcp/prompts")
@RequiredArgsConstructor
@Slf4j
public class McpPromptController {
    private final McpPromptService mcpPromptService;
    private final McpServerService mcpServerService;

    @GetMapping("/{serverId}")
    public ResponseEntity<List<McpPrompt>> listPrompts(@PathVariable String serverId) {
        Optional<McpServer> serverOpt = mcpServerService.getServer(serverId);
        if (serverOpt.isEmpty()) {
            log.warn("No server found for id: {}", serverId);
            return ResponseEntity.status(404).body(Collections.emptyList());
        }
        List<McpPrompt> prompts = mcpPromptService.getPrompts(serverOpt.get());
        log.info("Prompts found for server {}: {}", serverId, prompts.size());
        return ResponseEntity.ok(prompts);
    }

    @PostMapping("/{serverId}/{promptName}")
    public ResponseEntity<String> getPrompt(@PathVariable String serverId, 
                                          @PathVariable String promptName,
                                          @RequestBody(required = false) Map<String, Object> arguments) {
        Optional<McpServer> serverOpt = mcpServerService.getServer(serverId);
        if (serverOpt.isEmpty()) {
            log.warn("No server found for id: {}", serverId);
            return ResponseEntity.notFound().build();
        }
        String prompt = mcpPromptService.getPrompt(serverOpt.get(), promptName, arguments);
        if (prompt != null) {
            return ResponseEntity.ok(prompt);
        } else {
            return ResponseEntity.notFound().build();
        }
    }
}