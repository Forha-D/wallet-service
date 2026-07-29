package com.nexpay.wallet_service.controller;

import com.nexpay.wallet_service.model.OutboxStatus;
import com.nexpay.wallet_service.repository.OutboxRepository;
//import com.nexpay.wallet_service.repository.WalletRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.sql.DataSource;
import java.sql.Connection;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/health")
@RequiredArgsConstructor
public class HealthController {

    private final DataSource       dataSource;
    private final OutboxRepository outboxRepository;

    // ─────────────────────────────────────────
    // PING — is process alive?
    // GET /health/ping
    // ─────────────────────────────────────────
    @GetMapping("/ping")
    public ResponseEntity<Map<String, Object>> ping() {
        return ResponseEntity.ok(Map.of(
                "status",    "ok",
                "service",   "wallet-service",
                "timestamp", Instant.now().toString()
        ));
    }

    // ─────────────────────────────────────────
    // LIVE — K8s liveness probe
    // GET /health/live
    // ─────────────────────────────────────────
    @GetMapping("/live")
    public ResponseEntity<Map<String, String>> live() {
        return ResponseEntity.ok(Map.of("status", "ok"));
    }

    // ─────────────────────────────────────────
    // READY — K8s readiness probe
    // GET /health/ready
    // ─────────────────────────────────────────
    @GetMapping("/ready")
    public ResponseEntity<Map<String, String>> ready() {
        try (Connection conn = dataSource.getConnection()) {
            if (conn.isValid(3)) {
                return ResponseEntity.ok(Map.of("status", "ready"));
            }
        } catch (Exception ex) {
            log.error("db health check failed: {}", ex.getMessage());
        }
        return ResponseEntity
                .status(503)
                .body(Map.of("status", "not ready"));
    }

    // ─────────────────────────────────────────
    // DETAILED — internal ops use only
    // GET /health/detailed
    // ─────────────────────────────────────────
    @GetMapping("/detailed")
    public ResponseEntity<Map<String, Object>> detailed() {
        Map<String, Object> services  = new HashMap<>();
        Map<String, Object> response  = new HashMap<>();
        boolean             isHealthy = true;

        // check PostgreSQL
        try (Connection conn = dataSource.getConnection()) {
            boolean dbOk = conn.isValid(3);
            services.put("postgresql", Map.of(
                    "status", dbOk ? "healthy" : "unhealthy"
            ));
            if (!dbOk) isHealthy = false;
        } catch (Exception ex) {
            services.put("postgresql", Map.of(
                    "status",  "unhealthy",
                    "message", ex.getMessage()
            ));
            isHealthy = false;
        }

        // check outbox pending count — alert if too high
        try {
            long pendingCount = outboxRepository.countByStatus(OutboxStatus.PENDING);
            long failedCount  = outboxRepository.countByStatus(OutboxStatus.FAILED);
            services.put("outbox", Map.of(
                    "status",        pendingCount > 100 ? "degraded" : "healthy",
                    "pending_count", pendingCount,
                    "failed_count",  failedCount
            ));
            if (pendingCount > 100) isHealthy = false;
        } catch (Exception ex) {
            services.put("outbox", Map.of(
                    "status",  "unhealthy",
                    "message", ex.getMessage()
            ));
            isHealthy = false;
        }

        response.put("status",    isHealthy ? "healthy" : "unhealthy");
        response.put("service",   "wallet-service");
        response.put("timestamp", Instant.now().toString());
        response.put("services",  services);

        return ResponseEntity
                .status(isHealthy ? 200 : 503)
                .body(response);
    }
}