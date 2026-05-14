package com.patchagent.controller;

import com.patchagent.model.ServerRow;
import com.patchagent.model.SessionCredentials;
import com.patchagent.service.ServerInventoryService;
import com.patchagent.service.SshService;
import com.patchagent.util.RowSelectionParser;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

@RestController
public class ServerController {

    private final ServerInventoryService inventoryService;
    private final SshService sshService;
    private final RowSelectionParser rowSelectionParser;
    private final Executor executor;

    public ServerController(ServerInventoryService inventoryService,
                            SshService sshService,
                            RowSelectionParser rowSelectionParser,
                            Executor taskExecutor) {
        this.inventoryService  = inventoryService;
        this.sshService        = sshService;
        this.rowSelectionParser = rowSelectionParser;
        this.executor          = taskExecutor;
    }

    // GET /api/servers
    @GetMapping("/api/servers")
    public ResponseEntity<?> getServers(HttpServletRequest request) {
        SessionCredentials creds = AuthController.getSessionCredentials(request);
        String env = creds != null ? creds.getEnvironment() : null;
        try {
            List<ServerRow> rows = inventoryService.loadServers(env);
            List<String> clusters = rows.stream()
                .map(ServerRow::getCluster).distinct().sorted().collect(Collectors.toList());
            return ResponseEntity.ok(Map.of("rows", rows, "clusters", clusters));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

    // POST /api/status   body: { row_ids: [1,2,3] }
    @PostMapping("/api/status")
    public ResponseEntity<?> checkStatus(@RequestBody Map<String, Object> body,
                                         HttpServletRequest request) {
        // Use session credentials for SSH status checks
        SessionCredentials creds = AuthController.getSessionCredentials(request);
        String username = creds != null ? creds.getUsername() : null;
        String password = creds != null ? creds.getPassword() : null;

        try {
            List<ServerRow> allRows = inventoryService.loadServers();

            @SuppressWarnings("unchecked")
            List<Number> ids = (List<Number>) body.getOrDefault("row_ids", List.of());
            Set<Integer> requestedIds = ids.stream().map(Number::intValue).collect(Collectors.toSet());

            List<ServerRow> rows = requestedIds.isEmpty()
                ? allRows
                : allRows.stream().filter(r -> requestedIds.contains(r.getId())).toList();

            // Only check rows that have a status command
            Map<String, String> statuses = new ConcurrentHashMap<>();
            List<CompletableFuture<Void>> futures = new ArrayList<>();

            for (ServerRow row : rows) {
                int rid = row.getId();
                if (row.getStatusCmd() == null || row.getStatusCmd().isBlank()) {
                    statuses.put(String.valueOf(rid), "unknown");
                    continue;
                }
                futures.add(CompletableFuture.runAsync(() -> {
                    String status = sshService.checkStatus(row, username, password);
                    statuses.put(String.valueOf(rid), status);
                }, executor));
            }

            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                             .get(25, TimeUnit.SECONDS);

            return ResponseEntity.ok(Map.of("statuses", statuses));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

    // POST /api/resolve   body: { selection: "1,3-5" }
    @PostMapping("/api/resolve")
    public ResponseEntity<?> resolve(@RequestBody Map<String, Object> body) {
        try {
            String sel = String.valueOf(body.getOrDefault("selection", ""));
            List<ServerRow> rows = inventoryService.loadServers();
            List<Integer> allIds = rows.stream().map(ServerRow::getId).collect(Collectors.toList());
            List<Integer> matched = rowSelectionParser.parse(sel, allIds);
            return ResponseEntity.ok(Map.of("ids", matched, "count", matched.size()));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }
}
