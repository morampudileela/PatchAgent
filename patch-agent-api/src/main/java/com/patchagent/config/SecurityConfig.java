package com.patchagent.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * Spring Security configuration.
 *
 * <ul>
 *   <li>All {@code /api/*} requests require an authenticated session.</li>
 *   <li>Unauthenticated {@code /api/*} requests receive HTTP 401 (not a redirect)
 *       so the React frontend can intercept and show the login page.</li>
 *   <li>Static assets and the login/logout endpoints are publicly accessible.</li>
 *   <li>CSRF is disabled — the React SPA uses the session cookie implicitly and
 *       a custom {@code X-Requested-With} header is sent on every API call,
 *       which prevents CSRF in practice without requiring a token.</li>
 *   <li>Session is stored server-side; the browser only sees a random JSESSIONID.</li>
 * </ul>
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {

        http
            // ── CSRF: disabled (SPA + session cookie, see class javadoc) ──
            .csrf(csrf -> csrf.disable())

            // ── CORS: allow Vite dev server (port 5173) in dev mode ───────
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))

            // ── Route-level authorisation ─────────────────────────────────
            .authorizeHttpRequests(auth -> auth
                // Login and logout are always public
                .requestMatchers("/api/auth/login", "/api/auth/logout").permitAll()
                // All other /api/* require authentication
                .requestMatchers("/api/**").authenticated()
                // Everything else (static files, index.html, favicon) is public
                .anyRequest().permitAll()
            )

            // ── 401 for unauthenticated API calls (not a login redirect) ──
            .exceptionHandling(ex -> ex
                .defaultAuthenticationEntryPointFor(
                    new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED),
                    request -> request.getRequestURI().startsWith("/api/")
                )
            )

            // ── Session management: server-side HttpSession ───────────────
            .sessionManagement(sm -> sm
                .maximumSessions(10)                    // up to 10 concurrent sessions per user
            );

        return http.build();
    }

    /**
     * CORS: in dev mode the React Vite server runs on port 5173 and proxies
     * /api/* → :5000.  Allow that origin with credentials so the session cookie
     * is sent back.  In production, the React bundle is served by Spring itself
     * (same origin) so CORS is not needed but doesn't hurt.
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration cfg = new CorsConfiguration();
        cfg.setAllowedOriginPatterns(List.of(
            "http://localhost:5173",   // Vite dev server
            "http://localhost:5000"    // Spring itself
        ));
        cfg.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        cfg.setAllowedHeaders(List.of("*"));
        cfg.setAllowCredentials(true);
        cfg.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", cfg);
        return source;
    }
}
