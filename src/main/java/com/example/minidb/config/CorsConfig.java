package com.example.minidb.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Allows any browser origin to call the API. MiniDB has no authentication
 * (by design, see ProjectGuide.md #28), so this is safe for local/dev use
 * where the goal is to let a test frontend served from anywhere (including
 * a hosted HTML page) reach a MiniDB instance running on localhost.
 *
 * Do not deploy this configuration to a publicly reachable server.
 */
@Configuration
public class CorsConfig implements WebMvcConfigurer {

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOriginPatterns("*")
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*");
    }
}
