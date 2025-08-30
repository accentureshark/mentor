package org.shark.mentor.mcp.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.shark.mentor.mcp.model.McpResource;
import org.shark.mentor.mcp.model.McpServer;
import org.shark.mentor.mcp.service.McpResourceService;
import org.shark.mentor.mcp.service.McpServerService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/mcp/resources")
@RequiredArgsConstructor
@Slf4j
public class McpResourceController {
    private final McpResourceService mcpResourceService;
    private final McpServerService mcpServerService;

    @GetMapping("/{serverId}")
    public ResponseEntity<List<McpResource>> listResources(@PathVariable String serverId) {
        Optional<McpServer> serverOpt = mcpServerService.getServer(serverId);
        if (serverOpt.isEmpty()) {
            log.warn("No server found for id: {}", serverId);
            return ResponseEntity.status(404).body(Collections.emptyList());
        }
        List<McpResource> resources = mcpResourceService.getResources(serverOpt.get());
        log.info("Resources found for server {}: {}", serverId, resources.size());
        return ResponseEntity.ok(resources);
    }

    @PostMapping("/{serverId}/read")
    public ResponseEntity<String> readResource(@PathVariable String serverId, @RequestBody String uri) {
        Optional<McpServer> serverOpt = mcpServerService.getServer(serverId);
        if (serverOpt.isEmpty()) {
            log.warn("No server found for id: {}", serverId);
            return ResponseEntity.notFound().build();
        }
        String content = mcpResourceService.readResource(serverOpt.get(), uri);
        if (content != null) {
            return ResponseEntity.ok(content);
        } else {
            return ResponseEntity.notFound().build();
        }
    }
}