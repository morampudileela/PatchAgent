package com.patchagent.controller;

import com.patchagent.model.SessionCredentials;
import com.patchagent.service.LdapAuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Authentication endpoints.
 *
 * <pre>
 * POST /api/auth/login   — validate credentials against AD, create session
 * POST /api/auth/logout  — destroy current session
 * GET  /api/auth/me      — return the logged-in username (or 401)
 * </pre>
 *
 * <p>All endpoints consume / produce JSON.
 * The browser session is managed by Spring's {@link HttpSession}; the browser
 * receives only a {@code JSESSIONID} cookie — credentials never leave the server.
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private static final Logger log = LoggerFactory.getLogger(AuthController.class);

    /** Session TTL: 8 hours (matches AD password rotation window). */
    private static final int SESSION_MAX_AGE_SECONDS = 8 * 60 * 60;

    private final LdapAuthService ldapAuthService;

    public AuthController(LdapAuthService ldapAuthService) {
        this.ldapAuthService = ldapAuthService;
    }

    // ── POST /api/auth/login ─────────────────────────────────────────

    @PostMapping("/login")
    public ResponseEntity<Map<String, Object>> login(
            @RequestBody Map<String, String> body,
            HttpServletRequest request) {

        String username    = body.getOrDefault("username", "").trim();
        String password    = body.getOrDefault("password", "");
        String environment = body.getOrDefault("environment", "nonprod");
        if (!"prod".equalsIgnoreCase(environment)) environment = "nonprod";

        if (username.isBlank() || password.isBlank()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Username and password are required."));
        }

        // Validate against Active Directory for the chosen environment
        LdapAuthService.AuthResult result =
                ldapAuthService.authenticate(username, password, environment);

        if (!result.success()) {
            return ResponseEntity.status(401)
                    .body(Map.of("error", result.errorMessage()));
        }

        // Invalidate any existing session and create a fresh one (session fixation protection)
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        session = request.getSession(true);
        session.setMaxInactiveInterval(SESSION_MAX_AGE_SECONDS);

        // Store credentials + environment server-side — only a session ID goes to the browser
        session.setAttribute(SessionCredentials.SESSION_KEY,
                new SessionCredentials(username, password, environment));

        log.info("Session created for user '{}' (environment={})", username, environment);
        return ResponseEntity.ok(Map.of("username", username, "environment", environment));
    }

    // ── POST /api/auth/logout ────────────────────────────────────────

    @PostMapping("/logout")
    public ResponseEntity<Map<String, String>> logout(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            Object creds = session.getAttribute(SessionCredentials.SESSION_KEY);
            if (creds instanceof SessionCredentials sc) {
                log.info("Session destroyed for user '{}'", sc.getUsername());
            }
            session.invalidate();
        }
        return ResponseEntity.ok(Map.of("message", "Logged out."));
    }

    // ── GET /api/auth/me ─────────────────────────────────────────────

    @GetMapping("/me")
    public ResponseEntity<Map<String, String>> me(HttpServletRequest request) {
        SessionCredentials creds = getSessionCredentials(request);
        if (creds == null) {
            return ResponseEntity.status(401)
                    .body(Map.of("error", "Not authenticated."));
        }
        return ResponseEntity.ok(Map.of(
                "username",    creds.getUsername(),
                "environment", creds.getEnvironment()));
    }

    // ── Static helper ─────────────────────────────────────────────────

    /**
     * Retrieves {@link SessionCredentials} from the current HTTP session,
     * or returns null if the session is missing / expired / not logged in.
     */
    public static SessionCredentials getSessionCredentials(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null) return null;
        Object attr = session.getAttribute(SessionCredentials.SESSION_KEY);
        return (attr instanceof SessionCredentials sc) ? sc : null;
    }
}
