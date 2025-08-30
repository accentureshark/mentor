package org.shark.mentor.mcp.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.shark.mentor.mcp.model.McpCapabilities;
import org.shark.mentor.mcp.model.McpServer;
import org.shark.mentor.mcp.service.McpCapabilityService;
import org.shark.mentor.mcp.service.McpServerService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;

@RestController
@RequestMapping("/api/mcp/capabilities")
@RequiredArgsConstructor
@Slf4j
public class McpCapabilityController {
    private final McpCapabilityService mcpCapabilityService;
    private final McpServerService mcpServerService;

    @GetMapping("/{serverId}")
    public ResponseEntity<McpCapabilities> getCapabilities(@PathVariable String serverId) {
        Optional<McpServer> serverOpt = mcpServerService.getServer(serverId);
        if (serverOpt.isEmpty()) {
            log.warn("No server found for id: {}", serverId);
            return ResponseEntity.notFound().build();
        }
        
        McpServer server = serverOpt.get();
        if (server.getCapabilities() != null) {
            log.info("Returning cached capabilities for server: {}", serverId);
            return ResponseEntity.ok(server.getCapabilities());
        }
        
        McpCapabilities capabilities = mcpCapabilityService.discoverCapabilities(server);
        // Cache the capabilities in the server object
        server.setCapabilities(capabilities);
        log.info("Discovered capabilities for server {}: {}", serverId, capabilities);
        return ResponseEntity.ok(capabilities);
    }

    @PostMapping("/{serverId}/refresh")
    public ResponseEntity<McpCapabilities> refreshCapabilities(@PathVariable String serverId) {
        Optional<McpServer> serverOpt = mcpServerService.getServer(serverId);
        if (serverOpt.isEmpty()) {
            log.warn("No server found for id: {}", serverId);
            return ResponseEntity.notFound().build();
        }
        
        McpServer server = serverOpt.get();
        McpCapabilities capabilities = mcpCapabilityService.discoverCapabilities(server);
        server.setCapabilities(capabilities);
        log.info("Refreshed capabilities for server {}: {}", serverId, capabilities);
        return ResponseEntity.ok(capabilities);
    }
}