package org.shark.mentor.mcp.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.shark.mentor.mcp.model.McpServer;
import org.shark.mentor.mcp.service.McpServerService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;

@RestController
@RequestMapping("/api/mcp/servers")
@RequiredArgsConstructor
@Slf4j
public class McpServerStatusController {
    private final McpServerService mcpServerService;

    @PutMapping("/{id}/status")
    public ResponseEntity<McpServer> updateServerStatus(@PathVariable String id, @RequestBody StatusUpdateRequest request) {
        Optional<McpServer> serverOpt = mcpServerService.getServer(id);
        if (serverOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        McpServer server = serverOpt.get();
        server.setStatus(request.status);
        server.setLastError(request.lastError != null ? request.lastError : "");
        log.info("Updated status for server {}: {}", id, request.status);
        return ResponseEntity.ok(server);
    }

    public static class StatusUpdateRequest {
        public String status;
        public String lastError;
    }
}

