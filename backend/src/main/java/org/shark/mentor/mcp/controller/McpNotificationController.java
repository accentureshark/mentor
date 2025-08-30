package org.shark.mentor.mcp.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.shark.mentor.mcp.model.McpNotification;
import org.shark.mentor.mcp.service.McpNotificationService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/mcp/notifications")
@RequiredArgsConstructor
@Slf4j
public class McpNotificationController {
    private final McpNotificationService mcpNotificationService;

    @GetMapping
    public ResponseEntity<List<McpNotification>> getAllNotifications() {
        List<McpNotification> notifications = mcpNotificationService.getAllNotifications();
        return ResponseEntity.ok(notifications);
    }

    @GetMapping("/{serverId}")
    public ResponseEntity<List<McpNotification>> getServerNotifications(@PathVariable String serverId) {
        List<McpNotification> notifications = mcpNotificationService.getNotifications(serverId);
        return ResponseEntity.ok(notifications);
    }

    @PostMapping("/{serverId}")
    public ResponseEntity<Void> handleNotification(@PathVariable String serverId,
                                                  @RequestParam String method,
                                                  @RequestBody(required = false) Object params) {
        mcpNotificationService.handleNotification(serverId, method, params);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{serverId}")
    public ResponseEntity<Void> clearServerNotifications(@PathVariable String serverId) {
        mcpNotificationService.clearNotifications(serverId);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping
    public ResponseEntity<Void> clearAllNotifications() {
        mcpNotificationService.clearAllNotifications();
        return ResponseEntity.ok().build();
    }
}