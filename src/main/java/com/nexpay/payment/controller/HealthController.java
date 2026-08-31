package com.nexpay.payment.controller;

import com.nexpay.payment.model.OutboxStatus;
import com.nexpay.payment.model.PaymentStatus;
import com.nexpay.payment.repository.PaymentOutboxRepository;
import com.nexpay.payment.repository.PaymentRepository;
import com.nexpay.payment.repository.WebhookLogRepository;
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

    private final DataSource               dataSource;
    private final PaymentRepository        paymentRepository;
    private final PaymentOutboxRepository  paymentOutboxRepository;
    private final WebhookLogRepository     webhookLogRepository;

    // ─────────────────────────────────────────
    // PING
    // GET /health/ping
    // ─────────────────────────────────────────
    @GetMapping("/ping")
    public ResponseEntity<Map<String, Object>> ping() {
        return ResponseEntity.ok(Map.of(
                "status",    "ok",
                "service",   "payment-service",
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
    // DETAILED — internal ops only
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

        // check outbox pending count
        try {
            long pendingCount = paymentOutboxRepository
                    .countByStatus(OutboxStatus.PENDING);
            long failedCount  = paymentOutboxRepository
                    .countByStatus(OutboxStatus.FAILED);
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

        // check unverified webhooks — alert if any
        try {
            long unverifiedCount = webhookLogRepository.countByVerifiedFalse();
            services.put("webhooks", Map.of(
                    "status",           unverifiedCount > 10 ? "degraded" : "healthy",
                    "unverified_count", unverifiedCount
            ));
            if (unverifiedCount > 10) isHealthy = false;
        } catch (Exception ex) {
            services.put("webhooks", Map.of(
                    "status",  "unhealthy",
                    "message", ex.getMessage()
            ));
            isHealthy = false;
        }

        // check pending payments count
        try {
            long pendingPayments = paymentRepository
                    .countByStatus(PaymentStatus.PENDING);
            services.put("payments", Map.of(
                    "status",          "healthy",
                    "pending_payments", pendingPayments
            ));
        } catch (Exception ex) {
            services.put("payments", Map.of(
                    "status",  "unhealthy",
                    "message", ex.getMessage()
            ));
        }

        response.put("status",    isHealthy ? "healthy" : "unhealthy");
        response.put("service",   "payment-service");
        response.put("timestamp", Instant.now().toString());
        response.put("services",  services);

        return ResponseEntity
                .status(isHealthy ? 200 : 503)
                .body(response);
    }
}