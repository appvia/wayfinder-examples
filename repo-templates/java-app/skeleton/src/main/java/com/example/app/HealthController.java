package com.example.app;

import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Liveness and readiness for the Helm chart's probes.
 *
 * <p>Spring Actuator already serves {@code /actuator/health/liveness} and
 * {@code /actuator/health/readiness}, and those stay available for operators.
 * These two exist because every Wayfinder service template answers on
 * {@code /healthz} and {@code /readyz}, so one chart probes them all — and
 * because a probe that depends on actuator staying enabled is a probe that
 * breaks the day someone trims dependencies.
 *
 * <p>Keep both cheap and dependency-free. A readiness check that talks to a
 * database will take the pod out of service the moment the database hiccups,
 * which is rarely what you want.
 */
@RestController
public class HealthController {

  @GetMapping("/healthz")
  public Map<String, String> healthz() {
    return Map.of("status", "ok");
  }

  @GetMapping("/readyz")
  public Map<String, String> readyz() {
    return Map.of("status", "ok");
  }
}
