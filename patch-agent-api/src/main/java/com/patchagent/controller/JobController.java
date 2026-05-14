package com.patchagent.controller;

import com.patchagent.model.*;
import com.patchagent.service.JobExecutorService;
import com.patchagent.service.ServerInventoryService;
import com.patchagent.util.RowSelectionParser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@RestController
public class JobController {

    /** In-memory job registry. ConcurrentHashMap for thread-safe access. */
    private final Map<String, JobState> jobs = new ConcurrentHashMap<>();

    private final ServerInventoryService inventoryService;
    private final JobExecutorService     jobExecutorService;
    private final RowSelectionParser     rowSelectionParser;
    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper;

    public JobController(ServerInventoryService inventoryService,
                         JobExecutorService jobExecutorService,
                         RowSelectionParser rowSelectionParser,
                         com.fasterxml.jackson.databind.ObjectMapper objectMapper) {
        this.inventoryService  = inventoryService;
        this.jobExecutorService = jobExecutorService;
        this.rowSelectionParser = rowSelectionParser;
        this.objectMapper      = objectMapper;
    }

    // POST /api/job/start
    @PostMapping("/api/job/start")
    public ResponseEntity<?> startJob(@RequestBody Map<String, Object> body,
                                      HttpServletRequest request) {
        String action  = String.valueOf(body.getOrDefault("action", "stop")).toLowerCase();
        boolean dryRun = Boolean.TRUE.equals(body.get("dry_run"));
        String selStr  = String.valueOf(body.getOrDefault("selection", "*"));

        if (!action.equals("stop") && !action.equals("start")) {
            return ResponseEntity.badRequest()
                .body(Map.of("error", "action must be 'stop' or 'start'"));
        }

        // Capture session credentials at request time (before the async thread starts)
        SessionCredentials creds = AuthController.getSessionCredentials(request);
        String username    = creds != null ? creds.getUsername()    : null;
        String password    = creds != null ? creds.getPassword()    : null;
        String environment = creds != null ? creds.getEnvironment() : null;

        try {
            // Only consider rows that belong to the session environment
            List<ServerRow> allRows = inventoryService.loadServers(environment);
            List<Integer> allIds = allRows.stream().map(ServerRow::getId).collect(Collectors.toList());
            List<Integer> selIds = rowSelectionParser.parse(selStr, allIds);
            Set<Integer> selSet  = new HashSet<>(selIds);
            List<ServerRow> selRows = allRows.stream()
                .filter(r -> selSet.contains(r.getId())).collect(Collectors.toList());

            if (selRows.isEmpty()) {
                return ResponseEntity.badRequest()
                    .body(Map.of("error", "No rows matched the selection"));
            }

            String jobId = UUID.randomUUID().toString();
            JobState job = new JobState(jobId, action, dryRun, selStr);
            jobs.put(jobId, job);

            // Launch background job — pass credentials captured above
            jobExecutorService.runJob(job, selRows, action, dryRun, username, password);

            return ResponseEntity.ok(
                Map.of("job_id", jobId, "server_count", selRows.size()));

        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

    // GET /api/job/stream/{jobId}   — SSE stream
    @GetMapping(value = "/api/job/stream/{jobId}",
                produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamJob(@PathVariable String jobId, HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-cache");
        response.setHeader("X-Accel-Buffering", "no");

        JobState job = jobs.get(jobId);
        if (job == null) {
            SseEmitter err = new SseEmitter();
            try { err.send(SseEmitter.event().data("{\"error\":\"job not found\"}")); }
            catch (IOException ignored) {}
            err.complete();
            return err;
        }

        // 10-minute emitter timeout (generous for long patch windows)
        SseEmitter emitter = new SseEmitter(10 * 60 * 1000L);

        // Drain the job's event queue in a separate thread, forwarding to the SSE emitter
        Thread drainThread = new Thread(() -> {
            try {
                while (true) {
                    // Wait up to 25s for next event (send heartbeat if nothing arrives)
                    LogEvent event = job.takeEvent(25_000);
                    if (event == null) {
                        // Heartbeat — keeps the connection alive
                        emitter.send(SseEmitter.event().name("heartbeat").data("{}"));
                        continue;
                    }
                    if (event.isSentinel()) {
                        // Job finished — signal the client
                        emitter.send(SseEmitter.event().name("done").data("{}"));
                        emitter.complete();
                        break;
                    }
                    String json = objectMapper.writeValueAsString(event);
                    emitter.send(SseEmitter.event().data(json));
                }
            } catch (Exception e) {
                emitter.completeWithError(e);
            }
        }, "sse-drain-" + jobId.substring(0, 8));
        drainThread.setDaemon(true);
        drainThread.start();

        return emitter;
    }

    // GET /api/job/{jobId}
    @GetMapping("/api/job/{jobId}")
    public ResponseEntity<?> getJob(@PathVariable String jobId) {
        JobState job = jobs.get(jobId);
        if (job == null) {
            return ResponseEntity.status(404).body(Map.of("error", "not found"));
        }
        return ResponseEntity.ok(Map.of(
            "status",  job.getStatus().name().toLowerCase(),
            "action",  job.getAction(),
            "results", job.getResults()
        ));
    }
}
