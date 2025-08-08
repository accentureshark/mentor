package org.shark.mentor.mcp.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.shark.mentor.mcp.model.McpServer;
import org.shark.mentor.mcp.service.McpServerService;
import org.shark.mentor.mcp.service.McpToolService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/mcp/tools")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
@Slf4j
public class McpToolController {
    private final McpToolService mcpToolService;
    private final McpServerService mcpServerService;

    /**
     * Lista las herramientas (tools) expuestas por el servidor MCP seleccionado
     */
    @GetMapping("/{serverId}")
    public ResponseEntity<List<Map<String, Object>>> listTools(@PathVariable String serverId) {
        long start = System.currentTimeMillis();
        log.info("Listando tools para el servidor {}", serverId);
        McpServer server = mcpServerService.getServer(serverId)
                .orElseThrow(() -> new IllegalArgumentException("Servidor no encontrado: " + serverId));
        List<Map<String, Object>> tools = mcpToolService.getTools(server);
        long duration = System.currentTimeMillis() - start;
        log.info("Se obtuvieron {} tools para el servidor {} en {} ms", tools.size(), serverId, duration);
        return ResponseEntity.ok(tools);
    }
}

