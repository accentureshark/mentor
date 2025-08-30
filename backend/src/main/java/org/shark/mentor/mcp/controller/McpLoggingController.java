package org.shark.mentor.mcp.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.shark.mentor.mcp.model.McpLogEntry;
import org.shark.mentor.mcp.service.McpLoggingService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/mcp/logging")
@RequiredArgsConstructor
@Slf4j
public class McpLoggingController {
    private final McpLoggingService mcpLoggingService;

    @GetMapping
    public ResponseEntity<List<McpLogEntry>> getAllLogs() {
        List<McpLogEntry> logs = mcpLoggingService.getAllLogs();
        return ResponseEntity.ok(logs);
    }

    @GetMapping("/{serverId}")
    public ResponseEntity<List<McpLogEntry>> getServerLogs(@PathVariable String serverId) {
        List<McpLogEntry> logs = mcpLoggingService.getLogs(serverId);
        return ResponseEntity.ok(logs);
    }

    @PostMapping("/{serverId}")
    public ResponseEntity<Void> logMessage(@PathVariable String serverId,
                                         @RequestParam String level,
                                         @RequestParam String message,
                                         @RequestParam(required = false) String data) {
        mcpLoggingService.logMessage(serverId, level, message, data);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{serverId}")
    public ResponseEntity<Void> clearServerLogs(@PathVariable String serverId) {
        mcpLoggingService.clearLogs(serverId);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping
    public ResponseEntity<Void> clearAllLogs() {
        mcpLoggingService.clearAllLogs();
        return ResponseEntity.ok().build();
    }
}