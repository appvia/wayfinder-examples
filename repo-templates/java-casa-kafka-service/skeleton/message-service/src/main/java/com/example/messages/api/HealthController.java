package com.example.messages.api;

import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.PropertySource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Answers the load balancer's health check and says which release is running. The
 * release is written into release.properties at build time from -Drelease.
 */
@RestController
@PropertySource("classpath:release.properties")
public class HealthController {

    private final String release;

    public HealthController(@Value("${release:dev}") String release) {
        this.release = release;
    }

    @GetMapping("/api/healthz")
    public Map<String, String> healthz() {
        return Map.of("status", "ok", "release", release);
    }
}
