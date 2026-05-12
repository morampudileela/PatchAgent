package com.patchagent.controller;

import com.patchagent.model.PatchSession;
import com.patchagent.service.StateService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
public class HistoryController {

    private final StateService stateService;

    public HistoryController(StateService stateService) {
        this.stateService = stateService;
    }

    // GET /api/history
    @GetMapping("/api/history")
    public ResponseEntity<?> getHistory() {
        try {
            List<PatchSession> sessions = stateService.loadRecentSessions(20);
            return ResponseEntity.ok(Map.of("sessions", sessions));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }
}
