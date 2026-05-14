package com.patchagent.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * CORS config for development (React dev server on port 5173 → API on port 5000).
 * In production, React is served from Spring's static/ folder so CORS is not needed,
 * but having it here is harmless.
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOriginPatterns(
                    "http://localhost:5173",  // Vite dev server
                    "http://localhost:3000"   // CRA fallback
                )
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true);     // required for session cookie to be sent
    }
}
