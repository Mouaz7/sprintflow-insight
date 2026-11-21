package com.sprintflowinsight.common;

import java.util.Map;
import org.springframework.boot.health.actuate.endpoint.HealthEndpoint;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Preserves the minimal public health contract while Actuator runs on its own listener. */
@RestController
class PublicHealth {
  private final HealthEndpoint health;

  PublicHealth(HealthEndpoint health) {
    this.health = health;
  }

  @GetMapping("/actuator/health")
  ResponseEntity<Map<String, String>> health() {
    String status = health.health().getStatus().getCode();
    return ResponseEntity.status(
            "UP".equals(status) ? HttpStatus.OK : HttpStatus.SERVICE_UNAVAILABLE)
        .body(Map.of("status", status));
  }
}
