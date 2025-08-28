package org.shark.mentor.mcp.controller;

import org.shark.mentor.mcp.model.McpServer;
import org.shark.mentor.mcp.service.McpServerService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

/**
 * REST controller for MCP server management
 */
@RestController
@RequestMapping("/api/mcp/servers")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
@Slf4j
public class McpServerController {

    private final McpServerService mcpServerService;

    @GetMapping
    public ResponseEntity<List<McpServer>> getAllServers() {
        log.info("Getting all MCP servers");
        List<McpServer> servers = mcpServerService.getAllServers();
        return ResponseEntity.ok(servers);
    }

    @GetMapping("/{id}")
    public ResponseEntity<McpServer> getServer(@PathVariable String id) {
        log.info("Getting MCP server: {}", id);
        return mcpServerService.getServer(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<McpServer> addServer(@RequestBody McpServer server) {
        log.info("Adding new MCP server: {}", server.getName());
        McpServer savedServer = mcpServerService.addServer(server);
        return ResponseEntity.ok(savedServer);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> removeServer(@PathVariable String id) {
        log.info("Removing MCP server: {}", id);
        boolean removed = mcpServerService.removeServer(id);
        if (removed) {
            return ResponseEntity.ok().build();
        } else {
            return ResponseEntity.notFound().build();
        }
    }

    @PutMapping("/{id}/status")
    public ResponseEntity<McpServer> updateServerStatus(
            @PathVariable String id,
            @RequestBody String status) {
        log.info("Updating server {} status to {}", id, status);
        McpServer updatedServer = mcpServerService.updateServerStatus(id, status);
        if (updatedServer != null) {
            return ResponseEntity.ok(updatedServer);
        } else {
            return ResponseEntity.notFound().build();
        }
    }

    @PostMapping("/{id}/connect")
    public ResponseEntity<McpServer> connectToServer(@PathVariable String id) {
        log.info("Attempting to connect to MCP server: {}", id);
        try {
            McpServer connectedServer = mcpServerService.connectToServer(id);
            return ResponseEntity.ok(connectedServer);
        } catch (IllegalArgumentException e) {
            log.error("Server not found: {}", id);
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            log.error("Failed to connect to server {}: {}", id, e.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }

    @PostMapping("/{id}/disconnect")
    public ResponseEntity<McpServer> disconnectFromServer(@PathVariable String id) {
        log.info("Attempting to disconnect from MCP server: {}", id);
        try {
            McpServer disconnectedServer = mcpServerService.disconnectFromServer(id);
            return ResponseEntity.ok(disconnectedServer);
        } catch (IllegalArgumentException e) {
            log.error("Server not found: {}", id);
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            log.error("Failed to disconnect from server {}: {}", id, e.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }

    @GetMapping("/{id}/ping")
    public ResponseEntity<String> pingServer(@PathVariable String id) {
        log.info("Pinging MCP server: {}", id);
        try {
            boolean ok = mcpServerService.pingServer(id);
            if (ok) {
                return ResponseEntity.ok("pong");
            } else {
                return ResponseEntity.status(503).body("unreachable");
            }
        } catch (IllegalArgumentException e) {
            log.error("Server not found: {}", id);
            return ResponseEntity.notFound().build();
        }
    }

    @GetMapping("/status")
    public ResponseEntity<String> getConnectionStatus() {
        long connectedCount = mcpServerService.getConnectedServersCount();
        long totalCount = mcpServerService.getAllServers().size();

        return ResponseEntity.ok(String.format("Connected: %d/%d servers", connectedCount, totalCount));
    }

    @PostMapping("/reload")
    public ResponseEntity<List<McpServer>> reloadConfiguration() {
        log.info("Reloading MCP server configuration");
        try {
            mcpServerService.reloadConfiguration();
            List<McpServer> servers = mcpServerService.getAllServers();
            log.info("Configuration reloaded successfully, {} servers loaded", servers.size());
            return ResponseEntity.ok(servers);
        } catch (Exception e) {
            log.error("Failed to reload configuration: {}", e.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }
}