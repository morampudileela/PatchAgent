package com.patchagent.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.patchagent.config.PatchingProperties;
import com.patchagent.model.PatchSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;

/**
 * Reads and writes patch_state.json.
 * Mirrors Python's _save_state() and _load_state().
 */
@Service
public class StateService {

    private static final Logger log = LoggerFactory.getLogger(StateService.class);
    private static final int MAX_SESSIONS = 50;

    private final PatchingProperties props;
    private final ObjectMapper objectMapper;

    public StateService(PatchingProperties props, ObjectMapper objectMapper) {
        this.props        = props;
        this.objectMapper = objectMapper;
    }

    public synchronized void saveSession(PatchSession session) {
        try {
            Map<String, Object> state = loadRaw();
            @SuppressWarnings("unchecked")
            List<Object> sessions = (List<Object>) state.computeIfAbsent("sessions", k -> new ArrayList<>());
            sessions.add(session);
            // Keep only the last N sessions
            if (sessions.size() > MAX_SESSIONS) {
                sessions.subList(0, sessions.size() - MAX_SESSIONS).clear();
            }
            Path path = resolvePath(props.getStatePath());
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(path.toFile(), state);
        } catch (IOException e) {
            log.error("Failed to save state: {}", e.getMessage());
        }
    }

    public List<PatchSession> loadRecentSessions(int limit) {
        try {
            Map<String, Object> state = loadRaw();
            @SuppressWarnings("unchecked")
            List<Object> raw = (List<Object>) state.getOrDefault("sessions", List.of());
            // Re-deserialize each entry as PatchSession
            List<PatchSession> sessions = new ArrayList<>();
            for (Object obj : raw) {
                try {
                    String json = objectMapper.writeValueAsString(obj);
                    sessions.add(objectMapper.readValue(json, PatchSession.class));
                } catch (Exception ignored) {}
            }
            // Return last N in reverse order (most recent first)
            int from = Math.max(0, sessions.size() - limit);
            List<PatchSession> recent = new ArrayList<>(sessions.subList(from, sessions.size()));
            Collections.reverse(recent);
            return recent;
        } catch (Exception e) {
            log.warn("Could not load state: {}", e.getMessage());
            return List.of();
        }
    }

    // ---------------------------------------------------------------------------

    @SuppressWarnings("unchecked")
    private Map<String, Object> loadRaw() throws IOException {
        Path path = resolvePath(props.getStatePath());
        if (!Files.exists(path)) return new LinkedHashMap<>();
        return objectMapper.readValue(path.toFile(), Map.class);
    }

    private Path resolvePath(String pathStr) {
        Path p = Path.of(pathStr);
        return p.isAbsolute() ? p : Path.of("").toAbsolutePath().resolve(p);
    }
}
