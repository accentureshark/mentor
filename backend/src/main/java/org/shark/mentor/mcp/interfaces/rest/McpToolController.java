package org.shark.mentor.mcp.interfaces.rest;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.shark.mentor.mcp.application.service.server.McpServerService;
import org.shark.mentor.mcp.application.service.tool.McpToolService;
import org.shark.mentor.mcp.domain.model.McpServer;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/mcp/tools")
@RequiredArgsConstructor
@Slf4j
public class McpToolController {
    private final McpToolService mcpToolService;
    private final McpServerService mcpServerService;

    @GetMapping("/{serverId}")
    public ResponseEntity<List<Map<String, Object>>> listTools(@PathVariable String serverId) {
        Optional<McpServer> serverOpt = mcpServerService.getServer(serverId);
        if (serverOpt.isEmpty()) {
            log.warn("No server found for id: {}", serverId);
            return ResponseEntity.status(404).body(Collections.emptyList());
        }
        List<Map<String, Object>> tools = mcpToolService.getTools(serverOpt.get());
        log.info("Tools found for server {}: {}", serverId, tools.size());
        return ResponseEntity.ok(tools);
    }
}
