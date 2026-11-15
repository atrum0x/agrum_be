package com.atrum.agrum.health;

import com.atrum.agrum.security.ProjectionGroup;
import io.swagger.v3.oas.annotations.Operation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.sql.DataSource;
import java.sql.Connection;
import java.util.Map;

/**
 * Public liveness check for uptime monitors and the Coolify container health check.
 * Reports UP only when Postgres answers. Redis is left out on purpose: the app keeps working
 * without it (every Redis call falls back to Postgres). Says nothing else about the server.
 */
@ProjectionGroup("Health check (always allowed, no grant needed)")
@RestController
@RequestMapping("/api/public/health")
public class HealthController {

    private static final Logger log = LoggerFactory.getLogger(HealthController.class);
    private static final int DB_TIMEOUT_SECONDS = 2;

    private final DataSource dataSource;

    public HealthController(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Operation(summary = "Check that the server and its database are up")
    @GetMapping
    public ResponseEntity<Map<String, String>> health() {
        try (Connection connection = dataSource.getConnection()) {
            if (connection.isValid(DB_TIMEOUT_SECONDS)) {
                return ResponseEntity.ok(Map.of("status", "UP"));
            }
            log.warn("Health check: database connection is not valid");
        } catch (Exception e) {
            log.warn("Health check: database unreachable: {}", e.getMessage());
        }
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of("status", "DOWN"));
    }
}
